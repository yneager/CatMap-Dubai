"""Individual-animal recognition on the PC: find the animal in a photo, crop it, and embed the crop.

* Detector: DETR ResNet-50 (Facebook, Apache-2.0; ONNX export Xenova/detr-resnet-50), trained on COCO, which has cat,
  dog, bird, horse, sheep and cow classes. It finds every animal in a photo, so a photo with two cats gives two
  boxes and the matcher uses the largest animal of the post's type (the same rule as the phone's ML Kit crop).
* Embedder: open-noodle/pet-recognition-base (Apache-2.0): frozen DINOv2-base + a 512-d projection, trained for
  re-identifying individual cats and dogs from whole-animal crops. The output is L2-normalised, so cosine similarity
  is a dot product.

Both run on the GPU through DirectML when there is one, otherwise on the CPU. `server.py` and `eval/evaluate.py` use
this module, so the evaluation measures exactly what the live matcher does.
"""
from __future__ import annotations

import io
import threading
import ast
import hashlib
import json
from model_manifest import download as pinned_download, identity
import os
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageFilter, ImageOps

HERE = Path(__file__).resolve().parent
MODELS = HERE / "models"
# For evaluation (the live worker uses the defaults): CATMAP_EMBEDDER=large picks open-noodle/pet-recognition-large
# (frozen DINOv2-large, same input and 512-d output, Apache-2.0), and CATMAP_FLIP_TTA=1 also embeds the mirror image of
# each crop and averages the two. Both change EMBEDDING_MODEL, so vectors made with other settings are never compared.
EMBEDDER = os.environ.get("CATMAP_EMBEDDER", "base")
if EMBEDDER not in ("small", "base", "large"):
    raise ValueError(f"CATMAP_EMBEDDER must be small, base or large, not {EMBEDDER!r}")
FLIP_TTA = os.environ.get("CATMAP_FLIP_TTA", "") == "1"
MODEL_NAME = f"pet-recognition-{EMBEDDER}"
MODEL_REPO = f"open-noodle/{MODEL_NAME}"
MODEL_FILE = "recognition/model.onnx"
DETECTOR_REPO = "Xenova/detr-resnet-50"
DETECTOR_NAME = "detr-resnet-50"
# Version of the stored embeddings: bump it when the crop, the preprocessing or the model changes, so old vectors are
# recomputed instead of being compared with new ones.
EMBEDDING_VERSION = 1
# Hash the actual preprocessing/cropping implementation, immutable weights and evaluation setting.
_tree = ast.parse(Path(__file__).read_text(encoding="utf-8"))
_pipeline = [ast.dump(n, include_attributes=False) for n in ast.walk(_tree)
             if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name in
             {"preprocess", "animals", "crop_animal", "crops", "embed_photos", "embed", "open_image", "__init__"}]
_pipeline += [ast.dump(n, include_attributes=False) for n in _tree.body if isinstance(n, ast.Assign)
              and any(isinstance(t, ast.Name) and t.id in {"MEAN", "STD", "COCO_ANIMALS", "APP_TYPE_CLASSES",
                      "DETECT_MIN", "CROP_MARGIN", "MIN_CROP_PX", "APP_FORMATS"} for t in n.targets)]
_identity = identity(MODEL_REPO) + identity(DETECTOR_REPO) + json.dumps(_pipeline) + str(FLIP_TTA)
EMBEDDING_MODEL = f"{MODEL_NAME}/" + hashlib.sha256(_identity.encode()).hexdigest()[:24]
# "" with the defaults; otherwise e.g. "-large-flip", for evaluation file names that don't overwrite the base ones.
VARIANT = "" if (EMBEDDER, FLIP_TTA) == ("base", False) else f"-{EMBEDDER}" + ("-flip" if FLIP_TTA else "")
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)
Image.MAX_IMAGE_PIXELS = 4096 * 4096
# Photos from the app are always JPEG (both apps re-encode them, and the rules refuse anything else), so bytes from
# Firestore are opened with the JPEG decoder only: a crafted file in another format never reaches Pillow's other,
# less hardened decoders (threat model S5). The evaluation scripts pass their own list for local datasets.
APP_FORMATS = ("JPEG",)

# COCO class ids (DETR's labels) for the app's animal types. Rabbits and "other" have no COCO class: any animal box
# is used for them, and if none is found the centre of the photo is.
COCO_ANIMALS = {16: "bird", 17: "cat", 18: "dog", 19: "horse", 20: "sheep", 21: "cow", 22: "elephant", 23: "bear",
                24: "zebra", 25: "giraffe"}
APP_TYPE_CLASSES = {"cat": {17}, "dog": {18}, "bird": {16}}
DETECT_MIN = 0.5
CROP_MARGIN = 0.10
MIN_CROP_PX = 40


def providers(cpu: bool) -> list[str]:
    wanted = ["CPUExecutionProvider"] if cpu else ["DmlExecutionProvider", "CPUExecutionProvider"]
    return [p for p in wanted if p in ort.get_available_providers()]


def open_image(data: bytes | Image.Image, formats: tuple[str, ...] = APP_FORMATS) -> Image.Image:
    if isinstance(data, Image.Image):
        return data.convert("RGB")
    with Image.open(io.BytesIO(data), formats=formats) as im:
        # Phones may store the rotation in EXIF; CatMap strips EXIF, but photos from elsewhere (evaluation sets) may not.
        return ImageOps.exif_transpose(im).convert("RGB")


class Model:
    """pet-recognition-base: 224x224 RGB, ImageNet normalisation, 512-d unit vectors."""

    def __init__(self, cpu: bool):
        from huggingface_hub import hf_hub_download

        path = pinned_download(MODEL_REPO, MODEL_FILE)
        self.session = ort.InferenceSession(path, providers=providers(cpu))
        self.run_lock = threading.Lock() # DirectML permits only one Run per session at a time.
        self.provider = self.session.get_providers()[0]
        self.input = self.session.get_inputs()[0].name

    @staticmethod
    def preprocess(image: bytes | Image.Image) -> np.ndarray:
        rgb = open_image(image).resize((224, 224), Image.BICUBIC)
        return ((np.asarray(rgb, dtype=np.float32) / 255.0 - MEAN) / STD).transpose(2, 0, 1)

    def embed(self, images: list, batch: int = 16) -> np.ndarray:
        if not images:
            return np.zeros((0, 512), dtype=np.float32)
        out = []
        for i in range(0, len(images), batch):
            x = np.stack([self.preprocess(b) for b in images[i:i + batch]]).astype(np.float32)
            if FLIP_TTA:
                # The crop and its mirror in one batch; the sum of two unit vectors is re-normalised below.
                with self.run_lock: y = self.session.run(None, {self.input: np.concatenate([x, x[..., ::-1].copy()])})[0]
                out.append(y[:len(x)] + y[len(x):])
            else:
                with self.run_lock: y = self.session.run(None, {self.input: x})[0]
                out.append(y)
        v = np.concatenate(out)
        return v / np.linalg.norm(v, axis=1, keepdims=True).clip(1e-9)


@dataclass
class Box:
    cls: int
    score: float
    x0: float
    y0: float
    x1: float
    y1: float

    @property
    def area(self) -> float:
        return max(0.0, self.x1 - self.x0) * max(0.0, self.y1 - self.y0)

    @property
    def label(self) -> str:
        return COCO_ANIMALS.get(self.cls, str(self.cls))


class Detector:
    """DETR ResNet-50. No non-maximum suppression is needed: DETR predicts each object once."""

    def __init__(self, cpu: bool, size: int = 800):
        from huggingface_hub import hf_hub_download

        path = pinned_download(DETECTOR_REPO, "onnx/model.onnx")
        self.session = ort.InferenceSession(path, providers=providers(cpu))
        self.provider = self.session.get_providers()[0]
        self.size = size

    def animals(self, image: Image.Image) -> list[Box]:
        """Animal boxes in pixel coordinates, best first. Every photo is resized to the same square: DETR copes with
        the stretch, and one input shape lets DirectML reuse its compiled graph instead of rebuilding it per photo."""
        w, h = image.size
        im = image.resize((self.size, self.size), Image.BILINEAR)
        x = ((np.asarray(im, dtype=np.float32) / 255.0 - MEAN) / STD).transpose(2, 0, 1)[None].astype(np.float32)
        logits, boxes = self.session.run(None, {"pixel_values": x, "pixel_mask": np.ones((1, 64, 64), dtype=np.int64)})
        e = np.exp(logits[0] - logits[0].max(-1, keepdims=True))
        p = (e / e.sum(-1, keepdims=True))[:, :-1]  # the last class is "no object"
        cls, score = p.argmax(-1), p.max(-1)
        out = []
        for c, s, (cx, cy, bw, bh) in zip(cls, score, boxes[0]):
            if int(c) in COCO_ANIMALS and s >= DETECT_MIN:
                out.append(Box(int(c), float(s), (cx - bw / 2) * w, (cy - bh / 2) * h, (cx + bw / 2) * w, (cy + bh / 2) * h))
        return sorted(out, key=lambda b: -b.score)


@dataclass
class Crop:
    image: Image.Image
    detected: bool          # False: no animal was found, the centre of the photo was used
    label: str = ""         # what the detector saw ("cat", "dog"...)
    score: float = 0.0
    animals: int = 0        # animals found in the photo (several cats in one photo)
    sharpness: float = 0.0  # variance of the edge image; very low means blurry
    small: bool = False     # the animal is tiny in the photo
    box: tuple = field(default_factory=tuple)

    @property
    def low_quality(self) -> bool:
        return self.small or self.sharpness < BLURRY


BLURRY = 60.0


def sharpness(im: Image.Image) -> float:
    g = np.asarray(im.convert("L").resize((128, 128)).filter(ImageFilter.FIND_EDGES), dtype=np.float32)
    return float(g[2:-2, 2:-2].var())


def crop_animal(image: Image.Image, detector: Detector | None, animal_type: str = "") -> Crop:
    """The main animal of [animal_type] (or of any kind), with a 10 % margin; the photo's centre if none is found."""
    boxes = detector.animals(image) if detector is not None else []
    wanted = APP_TYPE_CLASSES.get(animal_type)
    pool = [b for b in boxes if wanted is None or b.cls in wanted] or boxes
    w, h = image.size
    if pool:
        # The biggest confident animal: a loose duplicate box around the same cat scores lower and loses.
        b = max(pool, key=lambda b: b.area * b.score ** 2)
        mx, my = (b.x1 - b.x0) * CROP_MARGIN, (b.y1 - b.y0) * CROP_MARGIN
        box = (max(0, int(b.x0 - mx)), max(0, int(b.y0 - my)), min(w, int(b.x1 + mx)), min(h, int(b.y1 + my)))
        if box[2] - box[0] >= 8 and box[3] - box[1] >= 8:
            crop = image.crop(box)
            return Crop(crop, True, b.label, b.score, len(boxes), sharpness(crop),
                        min(crop.size) < MIN_CROP_PX or b.area < 0.02 * w * h, box)
    side = int(min(w, h) * 0.85)
    box = ((w - side) // 2, (h - side) // 2, (w - side) // 2 + side, (h - side) // 2 + side)
    crop = image.crop(box)
    return Crop(crop, False, "", 0.0, len(boxes), sharpness(crop), False, box)


class Recognizer:
    """Detector + embedder: photos in, one embedding per photo (the main animal) out."""

    def __init__(self, cpu: bool, model: Model | None = None, detector: Detector | None = None):
        self.model = model or Model(cpu)
        self.detector = detector if detector is not None else Detector(cpu)
        self.provider = self.model.provider

    def crops(self, photos: list, animal_type: str = "") -> list[Crop]:
        return [crop_animal(open_image(p), self.detector, animal_type) for p in photos]

    def embed_photos(self, photos: list, animal_type: str = "") -> tuple[np.ndarray, list[Crop]]:
        crops = self.crops(photos, animal_type)
        return self.model.embed([c.image for c in crops]), crops


def to_blob(v: np.ndarray) -> bytes:
    """A vector as little-endian float32 bytes (2 KB for 512 values), the format stored in Firestore."""
    return np.asarray(v, dtype="<f4").tobytes()


def from_blob(b: bytes) -> np.ndarray:
    return np.frombuffer(bytes(b), dtype="<f4").astype(np.float32)
