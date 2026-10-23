"""Actual matcher candidate selection and build exclusions on synthetic inputs. No network or models."""
import argparse
import gzip
import json
import os
from pathlib import Path
import queue
import random
import statistics
import sys
import time
import tracemalloc
from types import SimpleNamespace
import numpy as np
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/worker'))
sys.modules['recognition']=SimpleNamespace(EMBEDDING_MODEL='synthetic/v1')
import recovery
import matching

def rss():
    if os.name=='nt':
        import ctypes
        from ctypes import wintypes
        class Counters(ctypes.Structure):
            _fields_=[('cb',wintypes.DWORD),('PageFaultCount',wintypes.DWORD)]+[(key,ctypes.c_size_t) for key in ('PeakWorkingSetSize','WorkingSetSize','QuotaPeakPagedPoolUsage','QuotaPagedPoolUsage','QuotaPeakNonPagedPoolUsage','QuotaNonPagedPoolUsage','PagefileUsage','PeakPagefileUsage')]
        c=Counters();c.cb=ctypes.sizeof(c)
        ctypes.windll.kernel32.GetCurrentProcess.restype=wintypes.HANDLE
        ctypes.windll.psapi.GetProcessMemoryInfo.argtypes=[wintypes.HANDLE,ctypes.POINTER(Counters),wintypes.DWORD]
        if ctypes.windll.psapi.GetProcessMemoryInfo(ctypes.windll.kernel32.GetCurrentProcess(),ctypes.byref(c),c.cb):return c.WorkingSetSize
    else:
        import resource
        return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss*(1 if sys.platform=='darwin' else 1024)

def main():
    rng=random.Random(23);tracemalloc.start()
    worker=recovery.Recovery(None,None,queue.Queue(),'synthetic')
    target={'id':'lost','animalType':'dog','category':'lost','ownerId':'owner','status':'open','lat':25.2,'lng':55.27}
    posts=[{'id':str(i),'animalType':'dog','category':'spotted','ownerId':'other','status':'open','lat':rng.uniform(-70,70),'lng':rng.uniform(-170,170)} for i in range(5000)]
    posts += [{**target,'id':'near'+str(i),'category':'spotted','ownerId':'other','lat':25.2+i*.001} for i in range(20)]
    worker.posts={p['id']:p for p in posts};worker.posts['lost']=target
    def exhaustive():return [p for p in posts if matching.distance_km(target['lat'],target['lng'],p['lat'],p['lng'])<=recovery.PREFILTER_KM]
    assert {p['id'] for p in exhaustive()}=={p['id'] for p in worker.candidates(target)},'Spatial index omitted a candidate'
    def measure(fn):
        values=[]
        for _ in range(30):
            start=time.perf_counter();fn();values.append((time.perf_counter()-start)*1000)
        return {'median_ms':round(statistics.median(values),4),'p95_ms':round(sorted(values)[28],4)}
    index=json.loads((ROOT/'firestore.indexes.json').read_text())
    optional=list((ROOT/'web/dist/assets').glob('AdminScreens-*.js'))+list((ROOT/'web/dist/assets').glob('PosterScreen-*.js'))
    heap,peak=tracemalloc.get_traced_memory()
    return {'synthetic':True,'network_requests':0,'source_documents':len(posts),'matching_candidates':len(worker.candidates(target)),
        'exhaustive':measure(exhaustive),'indexed_warm':measure(lambda:worker.candidates(target)),
        'python_heap_bytes':heap,'python_heap_peak_bytes':peak,'process_rss_bytes':rss(),
        'composite_indexes':len(index['indexes']),'field_overrides':len(index['fieldOverrides']),'unqueried_field_exemptions':sum(not x['indexes'] for x in index['fieldOverrides']),
        'example_vector_payload_bytes':6*512*4,'example_vector_array_members_without_exemption':6,
        'optional_route_files':len(optional),'optional_route_uncompressed_bytes':sum(f.stat().st_size for f in optional),
        'optional_route_gzip_bytes':sum(len(gzip.compress(f.read_bytes())) for f in optional),
        'limits':'CPU synthetic fixture; RSS includes Python/numpy, no models; no Firebase billing, device transfer or first-interactive latency measured'}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path);args=parser.parse_args();value=json.dumps(main(),indent=2)
    if args.output:args.output.write_text(value+'\n',encoding='utf-8')
    print(value)
