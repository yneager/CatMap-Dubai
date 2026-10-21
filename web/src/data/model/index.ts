// Generated from Android Kotlin models. Run npm run models:generate.
import { Timestamp } from 'firebase/firestore';
export interface Post {
  id: string;
  ownerId: string;
  ownerName: string;
  ownerPhotoId: string;
  animalType: string;
  category: string;
  status: string;
  name: string;
  description: string;
  color: string;
  breed: string;
  features: string;
  sex: string;
  ageGroup: string;
  photoIds: string[];
  videoPath: string;
  lat: number;
  lng: number;
  approximate: boolean;
  areaRadiusM: number;
  neighborhood: string;
  lastSeenAt: number;
  reward: number;
  contactMethod: string;
  contactPhone: string;
  contactEmail: string;
  adoptionStatus: string;
  health: string;
  vaccinated: boolean;
  neutered: boolean;
  updatesCount: number;
  hidden: boolean;
  keywords: string[];
  createdAt: number;
  updatedAt: number;
  resolvedAt: number;
  lastActivityAt: number;
  fingerprint: number[];
  microchip: string;
  resolution: string;
  resolvedMatchId: string;
  geoCells: string[];
  searchTerms: string[];
}
export const PostDefaults: Post = {
  id: "",
  ownerId: "",
  ownerName: "",
  ownerPhotoId: "",
  animalType: "cat",
  category: "spotted",
  status: "open",
  name: "",
  description: "",
  color: "",
  breed: "",
  features: "",
  sex: "unknown",
  ageGroup: "unknown",
  photoIds: [],
  videoPath: "",
  lat: 0.0,
  lng: 0.0,
  approximate: false,
  areaRadiusM: 0,
  neighborhood: "",
  lastSeenAt: 0,
  reward: 0,
  contactMethod: "in_app",
  contactPhone: "",
  contactEmail: "",
  adoptionStatus: "available",
  health: "",
  vaccinated: false,
  neutered: false,
  updatesCount: 0,
  hidden: false,
  keywords: [],
  createdAt: 0,
  updatedAt: 0,
  resolvedAt: 0,
  lastActivityAt: 0,
  fingerprint: [],
  microchip: "unknown",
  resolution: "",
  resolvedMatchId: "",
  geoCells: [],
  searchTerms: [],
};
export function PostFromDoc(id: string, data: Record<string, unknown>): Post {
  return {
    id: id,
    ownerId: typeof data.ownerId === "string" ? data.ownerId : "",
    ownerName: typeof data.ownerName === "string" ? data.ownerName : "",
    ownerPhotoId: typeof data.ownerPhotoId === "string" ? data.ownerPhotoId : "",
    animalType: typeof data.animalType === "string" ? data.animalType : "cat",
    category: typeof data.category === "string" ? data.category : "spotted",
    status: typeof data.status === "string" ? data.status : "open",
    name: typeof data.name === "string" ? data.name : "",
    description: typeof data.description === "string" ? data.description : "",
    color: typeof data.color === "string" ? data.color : "",
    breed: typeof data.breed === "string" ? data.breed : "",
    features: typeof data.features === "string" ? data.features : "",
    sex: typeof data.sex === "string" ? data.sex : "unknown",
    ageGroup: typeof data.ageGroup === "string" ? data.ageGroup : "unknown",
    photoIds: Array.isArray(data.photoIds) ? data.photoIds.filter((v): v is string => typeof v === "string") : [],
    videoPath: typeof data.videoPath === "string" ? data.videoPath : "",
    lat: typeof data.lat === "number" && Number.isFinite(data.lat) ? data.lat : 0.0,
    lng: typeof data.lng === "number" && Number.isFinite(data.lng) ? data.lng : 0.0,
    approximate: typeof data.approximate === "boolean" ? data.approximate : false,
    areaRadiusM: typeof data.areaRadiusM === "number" && Number.isFinite(data.areaRadiusM) ? data.areaRadiusM : 0,
    neighborhood: typeof data.neighborhood === "string" ? data.neighborhood : "",
    lastSeenAt: typeof data.lastSeenAt === "number" && Number.isFinite(data.lastSeenAt) ? data.lastSeenAt : 0,
    reward: typeof data.reward === "number" && Number.isFinite(data.reward) ? data.reward : 0,
    contactMethod: typeof data.contactMethod === "string" ? data.contactMethod : "in_app",
    contactPhone: typeof data.contactPhone === "string" ? data.contactPhone : "",
    contactEmail: typeof data.contactEmail === "string" ? data.contactEmail : "",
    adoptionStatus: typeof data.adoptionStatus === "string" ? data.adoptionStatus : "available",
    health: typeof data.health === "string" ? data.health : "",
    vaccinated: typeof data.vaccinated === "boolean" ? data.vaccinated : false,
    neutered: typeof data.neutered === "boolean" ? data.neutered : false,
    updatesCount: typeof data.updatesCount === "number" && Number.isFinite(data.updatesCount) ? data.updatesCount : 0,
    hidden: typeof data.hidden === "boolean" ? data.hidden : false,
    keywords: Array.isArray(data.keywords) ? data.keywords.filter((v): v is string => typeof v === "string") : [],
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    updatedAt: typeof data.updatedAt === "number" && Number.isFinite(data.updatedAt) ? data.updatedAt : 0,
    resolvedAt: typeof data.resolvedAt === "number" && Number.isFinite(data.resolvedAt) ? data.resolvedAt : 0,
    lastActivityAt: typeof data.lastActivityAt === "number" && Number.isFinite(data.lastActivityAt) ? data.lastActivityAt : 0,
    fingerprint: Array.isArray(data.fingerprint) ? data.fingerprint.filter((v): v is number => typeof v === "number" && Number.isFinite(v)) : [],
    microchip: typeof data.microchip === "string" ? data.microchip : "unknown",
    resolution: typeof data.resolution === "string" ? data.resolution : "",
    resolvedMatchId: typeof data.resolvedMatchId === "string" ? data.resolvedMatchId : "",
    geoCells: Array.isArray(data.geoCells) ? data.geoCells.filter((v): v is string => typeof v === "string") : [],
    searchTerms: Array.isArray(data.searchTerms) ? data.searchTerms.filter((v): v is string => typeof v === "string") : [],
  };
}
export function PostToDoc(value: Post): Record<string, unknown> {
  return {
    ownerId: value.ownerId,
    ownerName: value.ownerName,
    ownerPhotoId: value.ownerPhotoId,
    animalType: value.animalType,
    category: value.category,
    status: value.status,
    name: value.name,
    description: value.description,
    color: value.color,
    breed: value.breed,
    features: value.features,
    sex: value.sex,
    ageGroup: value.ageGroup,
    photoIds: value.photoIds,
    videoPath: value.videoPath,
    lat: value.lat,
    lng: value.lng,
    approximate: value.approximate,
    areaRadiusM: Math.round(value.areaRadiusM),
    neighborhood: value.neighborhood,
    lastSeenAt: Math.round(value.lastSeenAt),
    reward: Math.round(value.reward),
    contactMethod: value.contactMethod,
    contactPhone: value.contactPhone,
    contactEmail: value.contactEmail,
    adoptionStatus: value.adoptionStatus,
    health: value.health,
    vaccinated: value.vaccinated,
    neutered: value.neutered,
    updatesCount: Math.round(value.updatesCount),
    hidden: value.hidden,
    keywords: value.keywords,
    createdAt: Math.round(value.createdAt),
    updatedAt: Math.round(value.updatedAt),
    resolvedAt: Math.round(value.resolvedAt),
    lastActivityAt: Math.round(value.lastActivityAt),
    fingerprint: value.fingerprint,
    microchip: value.microchip,
    resolution: value.resolution,
    resolvedMatchId: value.resolvedMatchId,
    geoCells: value.geoCells,
    searchTerms: value.searchTerms,
  };
}
export interface PostMatch {
  id: string;
  lostPostId: string;
  sightingPostId: string;
  lostOwnerId: string;
  sightingOwnerId: string;
  members: string[];
  animalType: string;
  score: number;
  visual: number;
  tier: string;
  method: string;
  model: string;
  distanceKm: number;
  daysApart: number;
  reasons: string[];
  source: string;
  ownerState: string;
  reporterState: string;
  createdBy: string;
  createdAt: number;
  updatedAt: number;
  reviewedAt: number;
  notified: boolean;
  verifiedBy: string;
  verifiedAt: number;
}
export const PostMatchDefaults: PostMatch = {
  id: "",
  lostPostId: "",
  sightingPostId: "",
  lostOwnerId: "",
  sightingOwnerId: "",
  members: [],
  animalType: "",
  score: 0.0,
  visual: -1.0,
  tier: "none",
  method: "details",
  model: "",
  distanceKm: 0.0,
  daysApart: 0.0,
  reasons: [],
  source: "device",
  ownerState: "new",
  reporterState: "new",
  createdBy: "",
  createdAt: 0,
  updatedAt: 0,
  reviewedAt: 0,
  notified: false,
  verifiedBy: "",
  verifiedAt: 0,
};
export function PostMatchFromDoc(id: string, data: Record<string, unknown>): PostMatch {
  return {
    id: id,
    lostPostId: typeof data.lostPostId === "string" ? data.lostPostId : "",
    sightingPostId: typeof data.sightingPostId === "string" ? data.sightingPostId : "",
    lostOwnerId: typeof data.lostOwnerId === "string" ? data.lostOwnerId : "",
    sightingOwnerId: typeof data.sightingOwnerId === "string" ? data.sightingOwnerId : "",
    members: Array.isArray(data.members) ? data.members.filter((v): v is string => typeof v === "string") : [],
    animalType: typeof data.animalType === "string" ? data.animalType : "",
    score: typeof data.score === "number" && Number.isFinite(data.score) ? data.score : 0.0,
    visual: typeof data.visual === "number" && Number.isFinite(data.visual) ? data.visual : -1.0,
    tier: typeof data.tier === "string" ? data.tier : "none",
    method: typeof data.method === "string" ? data.method : "details",
    model: typeof data.model === "string" ? data.model : "",
    distanceKm: typeof data.distanceKm === "number" && Number.isFinite(data.distanceKm) ? data.distanceKm : 0.0,
    daysApart: typeof data.daysApart === "number" && Number.isFinite(data.daysApart) ? data.daysApart : 0.0,
    reasons: Array.isArray(data.reasons) ? data.reasons.filter((v): v is string => typeof v === "string") : [],
    source: typeof data.source === "string" ? data.source : "device",
    ownerState: typeof data.ownerState === "string" ? data.ownerState : "new",
    reporterState: typeof data.reporterState === "string" ? data.reporterState : "new",
    createdBy: typeof data.createdBy === "string" ? data.createdBy : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    updatedAt: typeof data.updatedAt === "number" && Number.isFinite(data.updatedAt) ? data.updatedAt : 0,
    reviewedAt: typeof data.reviewedAt === "number" && Number.isFinite(data.reviewedAt) ? data.reviewedAt : 0,
    notified: typeof data.notified === "boolean" ? data.notified : false,
    verifiedBy: typeof data.verifiedBy === "string" ? data.verifiedBy : "",
    verifiedAt: typeof data.verifiedAt === "number" && Number.isFinite(data.verifiedAt) ? data.verifiedAt : 0,
  };
}
export function PostMatchToDoc(value: PostMatch): Record<string, unknown> {
  return {
    lostPostId: value.lostPostId,
    sightingPostId: value.sightingPostId,
    lostOwnerId: value.lostOwnerId,
    sightingOwnerId: value.sightingOwnerId,
    members: value.members,
    animalType: value.animalType,
    score: value.score,
    visual: value.visual,
    tier: value.tier,
    method: value.method,
    model: value.model,
    distanceKm: value.distanceKm,
    daysApart: value.daysApart,
    reasons: value.reasons,
    source: value.source,
    ownerState: value.ownerState,
    reporterState: value.reporterState,
    createdBy: value.createdBy,
    createdAt: Math.round(value.createdAt),
    updatedAt: Math.round(value.updatedAt),
    reviewedAt: Math.round(value.reviewedAt),
    notified: value.notified,
    verifiedBy: value.verifiedBy,
    verifiedAt: Math.round(value.verifiedAt),
  };
}
export interface PostUpdate {
  id: string;
  postId: string;
  kind: string;
  text: string;
  photoId: string;
  lat: number;
  lng: number;
  neighborhood: string;
  byUid: string;
  byName: string;
  createdAt: number;
}
export const PostUpdateDefaults: PostUpdate = {
  id: "",
  postId: "",
  kind: "sighting",
  text: "",
  photoId: "",
  lat: 0.0,
  lng: 0.0,
  neighborhood: "",
  byUid: "",
  byName: "",
  createdAt: 0,
};
export function PostUpdateFromDoc(id: string, data: Record<string, unknown>): PostUpdate {
  return {
    id: id,
    postId: typeof data.postId === "string" ? data.postId : "",
    kind: typeof data.kind === "string" ? data.kind : "sighting",
    text: typeof data.text === "string" ? data.text : "",
    photoId: typeof data.photoId === "string" ? data.photoId : "",
    lat: typeof data.lat === "number" && Number.isFinite(data.lat) ? data.lat : 0.0,
    lng: typeof data.lng === "number" && Number.isFinite(data.lng) ? data.lng : 0.0,
    neighborhood: typeof data.neighborhood === "string" ? data.neighborhood : "",
    byUid: typeof data.byUid === "string" ? data.byUid : "",
    byName: typeof data.byName === "string" ? data.byName : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
  };
}
export function PostUpdateToDoc(value: PostUpdate): Record<string, unknown> {
  return {
    postId: value.postId,
    kind: value.kind,
    text: value.text,
    photoId: value.photoId,
    lat: value.lat,
    lng: value.lng,
    neighborhood: value.neighborhood,
    byUid: value.byUid,
    byName: value.byName,
    createdAt: Math.round(value.createdAt),
  };
}
export interface AdoptionRequest {
  id: string;
  postId: string;
  postName: string;
  postPhotoId: string;
  ownerId: string;
  requesterId: string;
  requesterName: string;
  message: string;
  home: string;
  status: string;
  ownerNote: string;
  createdAt: number;
  decidedAt: number;
}
export const AdoptionRequestDefaults: AdoptionRequest = {
  id: "",
  postId: "",
  postName: "",
  postPhotoId: "",
  ownerId: "",
  requesterId: "",
  requesterName: "",
  message: "",
  home: "",
  status: "pending",
  ownerNote: "",
  createdAt: 0,
  decidedAt: 0,
};
export function AdoptionRequestFromDoc(id: string, data: Record<string, unknown>): AdoptionRequest {
  return {
    id: id,
    postId: typeof data.postId === "string" ? data.postId : "",
    postName: typeof data.postName === "string" ? data.postName : "",
    postPhotoId: typeof data.postPhotoId === "string" ? data.postPhotoId : "",
    ownerId: typeof data.ownerId === "string" ? data.ownerId : "",
    requesterId: typeof data.requesterId === "string" ? data.requesterId : "",
    requesterName: typeof data.requesterName === "string" ? data.requesterName : "",
    message: typeof data.message === "string" ? data.message : "",
    home: typeof data.home === "string" ? data.home : "",
    status: typeof data.status === "string" ? data.status : "pending",
    ownerNote: typeof data.ownerNote === "string" ? data.ownerNote : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    decidedAt: typeof data.decidedAt === "number" && Number.isFinite(data.decidedAt) ? data.decidedAt : 0,
  };
}
export function AdoptionRequestToDoc(value: AdoptionRequest): Record<string, unknown> {
  return {
    postId: value.postId,
    postName: value.postName,
    postPhotoId: value.postPhotoId,
    ownerId: value.ownerId,
    requesterId: value.requesterId,
    requesterName: value.requesterName,
    message: value.message,
    home: value.home,
    status: value.status,
    ownerNote: value.ownerNote,
    createdAt: Math.round(value.createdAt),
    decidedAt: Math.round(value.decidedAt),
  };
}
export interface SitterProfile {
  uid: string;
  name: string;
  photoId: string;
  headline: string;
  description: string;
  experience: string;
  petTypes: string[];
  services: string[];
  availability: string;
  rate: number;
  rateUnit: string;
  lat: number;
  lng: number;
  neighborhood: string;
  active: boolean;
  ratingSum: number;
  verified: boolean;
  ratingCount: number;
  lastReviewId: string;
  createdAt: number;
  updatedAt: number;
}
export const SitterProfileDefaults: SitterProfile = {
  uid: "",
  name: "",
  photoId: "",
  headline: "",
  description: "",
  experience: "",
  petTypes: [],
  services: [],
  availability: "",
  rate: 0,
  rateUnit: "visit",
  lat: 0.0,
  lng: 0.0,
  neighborhood: "",
  active: true,
  ratingSum: 0,
  verified: false,
  ratingCount: 0,
  lastReviewId: "",
  createdAt: 0,
  updatedAt: 0,
};
export function SitterProfileFromDoc(id: string, data: Record<string, unknown>): SitterProfile {
  return {
    uid: id,
    name: typeof data.name === "string" ? data.name : "",
    photoId: typeof data.photoId === "string" ? data.photoId : "",
    headline: typeof data.headline === "string" ? data.headline : "",
    description: typeof data.description === "string" ? data.description : "",
    experience: typeof data.experience === "string" ? data.experience : "",
    petTypes: Array.isArray(data.petTypes) ? data.petTypes.filter((v): v is string => typeof v === "string") : [],
    services: Array.isArray(data.services) ? data.services.filter((v): v is string => typeof v === "string") : [],
    availability: typeof data.availability === "string" ? data.availability : "",
    rate: typeof data.rate === "number" && Number.isFinite(data.rate) ? data.rate : 0,
    rateUnit: typeof data.rateUnit === "string" ? data.rateUnit : "visit",
    lat: typeof data.lat === "number" && Number.isFinite(data.lat) ? data.lat : 0.0,
    lng: typeof data.lng === "number" && Number.isFinite(data.lng) ? data.lng : 0.0,
    neighborhood: typeof data.neighborhood === "string" ? data.neighborhood : "",
    active: typeof data.active === "boolean" ? data.active : true,
    ratingSum: typeof data.ratingSum === "number" && Number.isFinite(data.ratingSum) ? data.ratingSum : 0,
    verified: typeof data.verified === "boolean" ? data.verified : false,
    ratingCount: typeof data.ratingCount === "number" && Number.isFinite(data.ratingCount) ? data.ratingCount : 0,
    lastReviewId: typeof data.lastReviewId === "string" ? data.lastReviewId : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    updatedAt: typeof data.updatedAt === "number" && Number.isFinite(data.updatedAt) ? data.updatedAt : 0,
  };
}
export function SitterProfileToDoc(value: SitterProfile): Record<string, unknown> {
  return {
    name: value.name,
    photoId: value.photoId,
    headline: value.headline,
    description: value.description,
    experience: value.experience,
    petTypes: value.petTypes,
    services: value.services,
    availability: value.availability,
    rate: Math.round(value.rate),
    rateUnit: value.rateUnit,
    lat: value.lat,
    lng: value.lng,
    neighborhood: value.neighborhood,
    active: value.active,
    ratingSum: Math.round(value.ratingSum),
    verified: value.verified,
    ratingCount: Math.round(value.ratingCount),
    lastReviewId: value.lastReviewId,
    createdAt: Math.round(value.createdAt),
    updatedAt: Math.round(value.updatedAt),
  };
}
export interface Booking {
  id: string;
  ownerId: string;
  ownerName: string;
  sitterId: string;
  sitterName: string;
  petTypes: string[];
  petCount: number;
  startAt: number;
  endAt: number;
  notes: string;
  sitterNote: string;
  status: string;
  reviewed: boolean;
  createdAt: number;
  updatedAt: number;
}
export const BookingDefaults: Booking = {
  id: "",
  ownerId: "",
  ownerName: "",
  sitterId: "",
  sitterName: "",
  petTypes: [],
  petCount: 1,
  startAt: 0,
  endAt: 0,
  notes: "",
  sitterNote: "",
  status: "requested",
  reviewed: false,
  createdAt: 0,
  updatedAt: 0,
};
export function BookingFromDoc(id: string, data: Record<string, unknown>): Booking {
  return {
    id: id,
    ownerId: typeof data.ownerId === "string" ? data.ownerId : "",
    ownerName: typeof data.ownerName === "string" ? data.ownerName : "",
    sitterId: typeof data.sitterId === "string" ? data.sitterId : "",
    sitterName: typeof data.sitterName === "string" ? data.sitterName : "",
    petTypes: Array.isArray(data.petTypes) ? data.petTypes.filter((v): v is string => typeof v === "string") : [],
    petCount: typeof data.petCount === "number" && Number.isFinite(data.petCount) ? data.petCount : 1,
    startAt: typeof data.startAt === "number" && Number.isFinite(data.startAt) ? data.startAt : 0,
    endAt: typeof data.endAt === "number" && Number.isFinite(data.endAt) ? data.endAt : 0,
    notes: typeof data.notes === "string" ? data.notes : "",
    sitterNote: typeof data.sitterNote === "string" ? data.sitterNote : "",
    status: typeof data.status === "string" ? data.status : "requested",
    reviewed: typeof data.reviewed === "boolean" ? data.reviewed : false,
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    updatedAt: typeof data.updatedAt === "number" && Number.isFinite(data.updatedAt) ? data.updatedAt : 0,
  };
}
export function BookingToDoc(value: Booking): Record<string, unknown> {
  return {
    ownerId: value.ownerId,
    ownerName: value.ownerName,
    sitterId: value.sitterId,
    sitterName: value.sitterName,
    petTypes: value.petTypes,
    petCount: Math.round(value.petCount),
    startAt: Math.round(value.startAt),
    endAt: Math.round(value.endAt),
    notes: value.notes,
    sitterNote: value.sitterNote,
    status: value.status,
    reviewed: value.reviewed,
    createdAt: Math.round(value.createdAt),
    updatedAt: Math.round(value.updatedAt),
  };
}
export interface Review {
  id: string;
  sitterId: string;
  authorId: string;
  authorName: string;
  rating: number;
  text: string;
  createdAt: number;
}
export const ReviewDefaults: Review = {
  id: "",
  sitterId: "",
  authorId: "",
  authorName: "",
  rating: 5,
  text: "",
  createdAt: 0,
};
export function ReviewFromDoc(id: string, data: Record<string, unknown>): Review {
  return {
    id: id,
    sitterId: typeof data.sitterId === "string" ? data.sitterId : "",
    authorId: typeof data.authorId === "string" ? data.authorId : "",
    authorName: typeof data.authorName === "string" ? data.authorName : "",
    rating: typeof data.rating === "number" && Number.isFinite(data.rating) ? data.rating : 5,
    text: typeof data.text === "string" ? data.text : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
  };
}
export function ReviewToDoc(value: Review): Record<string, unknown> {
  return {
    sitterId: value.sitterId,
    authorId: value.authorId,
    authorName: value.authorName,
    rating: Math.round(value.rating),
    text: value.text,
    createdAt: Math.round(value.createdAt),
  };
}
export interface Chat {
  id: string;
  members: string[];
  names: Record<string, string>;
  photos: Record<string, string>;
  lastText: string;
  lastSender: string;
  lastAt: number;
  unread: Record<string, number>;
}
export const ChatDefaults: Chat = {
  id: "",
  members: [],
  names: {},
  photos: {},
  lastText: "",
  lastSender: "",
  lastAt: 0,
  unread: {},
};
export function ChatFromDoc(id: string, data: Record<string, unknown>): Chat {
  return {
    id: id,
    members: Array.isArray(data.members) ? data.members.filter((v): v is string => typeof v === "string") : [],
    names: data.names && typeof data.names === "object" && !Array.isArray(data.names) ? Object.fromEntries(Object.entries(data.names).filter(([, v]) => typeof v === "string")) as Record<string, string> : {},
    photos: data.photos && typeof data.photos === "object" && !Array.isArray(data.photos) ? Object.fromEntries(Object.entries(data.photos).filter(([, v]) => typeof v === "string")) as Record<string, string> : {},
    lastText: typeof data.lastText === "string" ? data.lastText : "",
    lastSender: typeof data.lastSender === "string" ? data.lastSender : "",
    lastAt: typeof data.lastAt === "number" && Number.isFinite(data.lastAt) ? data.lastAt : 0,
    unread: data.unread && typeof data.unread === "object" && !Array.isArray(data.unread) ? Object.fromEntries(Object.entries(data.unread).filter(([, v]) => typeof v === "number" && Number.isFinite(v))) as Record<string, number> : {},
  };
}
export function ChatToDoc(value: Chat): Record<string, unknown> {
  return {
    members: value.members,
    names: value.names,
    photos: value.photos,
    lastText: value.lastText,
    lastSender: value.lastSender,
    lastAt: Math.round(value.lastAt),
    unread: Object.fromEntries(Object.entries(value.unread).map(([k,v])=>[k,Math.round(v)])),
  };
}
export interface Message {
  id: string;
  senderId: string;
  text: string;
  imageId: string;
  postId: string;
  postTitle: string;
  postPhotoId: string;
  matchId: string;
  matchPhotoId: string;
  createdAt: number;
}
export const MessageDefaults: Message = {
  id: "",
  senderId: "",
  text: "",
  imageId: "",
  postId: "",
  postTitle: "",
  postPhotoId: "",
  matchId: "",
  matchPhotoId: "",
  createdAt: 0,
};
export function MessageFromDoc(id: string, data: Record<string, unknown>): Message {
  return {
    id: id,
    senderId: typeof data.senderId === "string" ? data.senderId : "",
    text: typeof data.text === "string" ? data.text : "",
    imageId: typeof data.imageId === "string" ? data.imageId : "",
    postId: typeof data.postId === "string" ? data.postId : "",
    postTitle: typeof data.postTitle === "string" ? data.postTitle : "",
    postPhotoId: typeof data.postPhotoId === "string" ? data.postPhotoId : "",
    matchId: typeof data.matchId === "string" ? data.matchId : "",
    matchPhotoId: typeof data.matchPhotoId === "string" ? data.matchPhotoId : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
  };
}
export function MessageToDoc(value: Message): Record<string, unknown> {
  return {
    senderId: value.senderId,
    text: value.text,
    imageId: value.imageId,
    postId: value.postId,
    postTitle: value.postTitle,
    postPhotoId: value.postPhotoId,
    matchId: value.matchId,
    matchPhotoId: value.matchPhotoId,
    createdAt: Math.round(value.createdAt),
  };
}
export interface AreaFollow {
  uid: string;
  neighborhoods: string[];
  lat: number;
  lng: number;
  radiusKm: number;
  updatedAt: number;
}
export const AreaFollowDefaults: AreaFollow = {
  uid: "",
  neighborhoods: [],
  lat: 0.0,
  lng: 0.0,
  radiusKm: 0.0,
  updatedAt: 0,
};
export function AreaFollowFromDoc(id: string, data: Record<string, unknown>): AreaFollow {
  return {
    uid: id,
    neighborhoods: Array.isArray(data.neighborhoods) ? data.neighborhoods.filter((v): v is string => typeof v === "string") : [],
    lat: typeof data.lat === "number" && Number.isFinite(data.lat) ? data.lat : 0.0,
    lng: typeof data.lng === "number" && Number.isFinite(data.lng) ? data.lng : 0.0,
    radiusKm: typeof data.radiusKm === "number" && Number.isFinite(data.radiusKm) ? data.radiusKm : 0.0,
    updatedAt: typeof data.updatedAt === "number" && Number.isFinite(data.updatedAt) ? data.updatedAt : 0,
  };
}
export function AreaFollowToDoc(value: AreaFollow): Record<string, unknown> {
  return {
    neighborhoods: value.neighborhoods,
    lat: value.lat,
    lng: value.lng,
    radiusKm: value.radiusKm,
    updatedAt: Math.round(value.updatedAt),
  };
}
export interface Favorite {
  id: string;
  postId: string;
  savedAt: number;
}
export const FavoriteDefaults: Favorite = {
  id: "",
  postId: "",
  savedAt: 0,
};
export function FavoriteFromDoc(id: string, data: Record<string, unknown>): Favorite {
  return {
    id: id,
    postId: typeof data.postId === "string" ? data.postId : "",
    savedAt: typeof data.savedAt === "number" && Number.isFinite(data.savedAt) ? data.savedAt : 0,
  };
}
export function FavoriteToDoc(value: Favorite): Record<string, unknown> {
  return {
    postId: value.postId,
    savedAt: Math.round(value.savedAt),
  };
}
export interface UserProfile {
  uid: string;
  username: string;
  displayName: string;
  bio: string;
  photoId: string;
  role: string;
  volunteerStatus: string;
  stats: Record<string, number>;
  points: number;
  neighborhoods: string[];
  badges: string[];
  followedCats: string[];
  showStatsPublicly: boolean;
  suspended: boolean;
  lastPostAt: Timestamp | null;
  createdAt: number;
}
export const UserProfileDefaults: UserProfile = {
  uid: "",
  username: "",
  displayName: "",
  bio: "",
  photoId: "",
  role: "user",
  volunteerStatus: "none",
  stats: {},
  points: 0,
  neighborhoods: [],
  badges: [],
  followedCats: [],
  showStatsPublicly: true,
  suspended: false,
  lastPostAt: null,
  createdAt: 0,
};
export function UserProfileFromDoc(id: string, data: Record<string, unknown>): UserProfile {
  return {
    uid: id,
    username: typeof data.username === "string" ? data.username : "",
    displayName: typeof data.displayName === "string" ? data.displayName : "",
    bio: typeof data.bio === "string" ? data.bio : "",
    photoId: typeof data.photoId === "string" ? data.photoId : "",
    role: typeof data.role === "string" ? data.role : "user",
    volunteerStatus: typeof data.volunteerStatus === "string" ? data.volunteerStatus : "none",
    stats: data.stats && typeof data.stats === "object" && !Array.isArray(data.stats) ? Object.fromEntries(Object.entries(data.stats).filter(([, v]) => typeof v === "number" && Number.isFinite(v))) as Record<string, number> : {},
    points: typeof data.points === "number" && Number.isFinite(data.points) ? data.points : 0,
    neighborhoods: Array.isArray(data.neighborhoods) ? data.neighborhoods.filter((v): v is string => typeof v === "string") : [],
    badges: Array.isArray(data.badges) ? data.badges.filter((v): v is string => typeof v === "string") : [],
    followedCats: Array.isArray(data.followedCats) ? data.followedCats.filter((v): v is string => typeof v === "string") : [],
    showStatsPublicly: typeof data.showStatsPublicly === "boolean" ? data.showStatsPublicly : true,
    suspended: typeof data.suspended === "boolean" ? data.suspended : false,
    lastPostAt: data.lastPostAt instanceof Timestamp ? data.lastPostAt : null,
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
  };
}
export function UserProfileToDoc(value: UserProfile): Record<string, unknown> {
  return {
    username: value.username,
    displayName: value.displayName,
    bio: value.bio,
    photoId: value.photoId,
    role: value.role,
    volunteerStatus: value.volunteerStatus,
    stats: Object.fromEntries(Object.entries(value.stats).map(([k,v])=>[k,Math.round(v)])),
    points: Math.round(value.points),
    neighborhoods: value.neighborhoods,
    badges: value.badges,
    followedCats: value.followedCats,
    showStatsPublicly: value.showStatsPublicly,
    suspended: value.suspended,
    lastPostAt: value.lastPostAt,
    createdAt: Math.round(value.createdAt),
  };
}
export interface Cat {
  id: string;
  name: string;
  aliases: string[];
  description: string;
  distinctiveMarks: string;
  coatColor: string;
  pattern: string;
  size: string;
  sex: string;
  breedHints: string[];
  photoIds: string[];
  coverPhotoId: string;
  createdBy: string;
  createdByName: string;
  contributors: string[];
  followers: string[];
  firstSeen: number;
  lastSeen: number;
  sightingCount: number;
  lat: number;
  lng: number;
  neighborhood: string;
  neighborhoods: string[];
  tnrStatus: string;
  tnrVerified: boolean;
  healthStatus: string;
  features: number[];
  mergedInto: string;
  hidden: boolean;
  createdAt: number;
  updatedAt: number;
  geoCells: string[];
  searchTerms: string[];
}
export const CatDefaults: Cat = {
  id: "",
  name: "",
  aliases: [],
  description: "",
  distinctiveMarks: "",
  coatColor: "unknown",
  pattern: "unknown",
  size: "unknown",
  sex: "unknown",
  breedHints: [],
  photoIds: [],
  coverPhotoId: "",
  createdBy: "",
  createdByName: "",
  contributors: [],
  followers: [],
  firstSeen: 0,
  lastSeen: 0,
  sightingCount: 0,
  lat: 0.0,
  lng: 0.0,
  neighborhood: "",
  neighborhoods: [],
  tnrStatus: "unknown",
  tnrVerified: false,
  healthStatus: "unknown",
  features: [],
  mergedInto: "",
  hidden: false,
  createdAt: 0,
  updatedAt: 0,
  geoCells: [],
  searchTerms: [],
};
export function CatFromDoc(id: string, data: Record<string, unknown>): Cat {
  return {
    id: id,
    name: typeof data.name === "string" ? data.name : "",
    aliases: Array.isArray(data.aliases) ? data.aliases.filter((v): v is string => typeof v === "string") : [],
    description: typeof data.description === "string" ? data.description : "",
    distinctiveMarks: typeof data.distinctiveMarks === "string" ? data.distinctiveMarks : "",
    coatColor: typeof data.coatColor === "string" ? data.coatColor : "unknown",
    pattern: typeof data.pattern === "string" ? data.pattern : "unknown",
    size: typeof data.size === "string" ? data.size : "unknown",
    sex: typeof data.sex === "string" ? data.sex : "unknown",
    breedHints: Array.isArray(data.breedHints) ? data.breedHints.filter((v): v is string => typeof v === "string") : [],
    photoIds: Array.isArray(data.photoIds) ? data.photoIds.filter((v): v is string => typeof v === "string") : [],
    coverPhotoId: typeof data.coverPhotoId === "string" ? data.coverPhotoId : "",
    createdBy: typeof data.createdBy === "string" ? data.createdBy : "",
    createdByName: typeof data.createdByName === "string" ? data.createdByName : "",
    contributors: Array.isArray(data.contributors) ? data.contributors.filter((v): v is string => typeof v === "string") : [],
    followers: Array.isArray(data.followers) ? data.followers.filter((v): v is string => typeof v === "string") : [],
    firstSeen: typeof data.firstSeen === "number" && Number.isFinite(data.firstSeen) ? data.firstSeen : 0,
    lastSeen: typeof data.lastSeen === "number" && Number.isFinite(data.lastSeen) ? data.lastSeen : 0,
    sightingCount: typeof data.sightingCount === "number" && Number.isFinite(data.sightingCount) ? data.sightingCount : 0,
    lat: typeof data.lat === "number" && Number.isFinite(data.lat) ? data.lat : 0.0,
    lng: typeof data.lng === "number" && Number.isFinite(data.lng) ? data.lng : 0.0,
    neighborhood: typeof data.neighborhood === "string" ? data.neighborhood : "",
    neighborhoods: Array.isArray(data.neighborhoods) ? data.neighborhoods.filter((v): v is string => typeof v === "string") : [],
    tnrStatus: typeof data.tnrStatus === "string" ? data.tnrStatus : "unknown",
    tnrVerified: typeof data.tnrVerified === "boolean" ? data.tnrVerified : false,
    healthStatus: typeof data.healthStatus === "string" ? data.healthStatus : "unknown",
    features: Array.isArray(data.features) ? data.features.filter((v): v is number => typeof v === "number" && Number.isFinite(v)) : [],
    mergedInto: typeof data.mergedInto === "string" ? data.mergedInto : "",
    hidden: typeof data.hidden === "boolean" ? data.hidden : false,
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    updatedAt: typeof data.updatedAt === "number" && Number.isFinite(data.updatedAt) ? data.updatedAt : 0,
    geoCells: Array.isArray(data.geoCells) ? data.geoCells.filter((v): v is string => typeof v === "string") : [],
    searchTerms: Array.isArray(data.searchTerms) ? data.searchTerms.filter((v): v is string => typeof v === "string") : [],
  };
}
export function CatToDoc(value: Cat): Record<string, unknown> {
  return {
    name: value.name,
    aliases: value.aliases,
    description: value.description,
    distinctiveMarks: value.distinctiveMarks,
    coatColor: value.coatColor,
    pattern: value.pattern,
    size: value.size,
    sex: value.sex,
    breedHints: value.breedHints,
    photoIds: value.photoIds,
    coverPhotoId: value.coverPhotoId,
    createdBy: value.createdBy,
    createdByName: value.createdByName,
    contributors: value.contributors,
    followers: value.followers,
    firstSeen: Math.round(value.firstSeen),
    lastSeen: Math.round(value.lastSeen),
    sightingCount: Math.round(value.sightingCount),
    lat: value.lat,
    lng: value.lng,
    neighborhood: value.neighborhood,
    neighborhoods: value.neighborhoods,
    tnrStatus: value.tnrStatus,
    tnrVerified: value.tnrVerified,
    healthStatus: value.healthStatus,
    features: value.features,
    mergedInto: value.mergedInto,
    hidden: value.hidden,
    createdAt: Math.round(value.createdAt),
    updatedAt: Math.round(value.updatedAt),
    geoCells: value.geoCells,
    searchTerms: value.searchTerms,
  };
}
export interface Sighting {
  id: string;
  catId: string;
  catName: string;
  userId: string;
  username: string;
  photoIds: string[];
  seenAt: number;
  lat: number;
  lng: number;
  neighborhood: string;
  locationSource: string;
  coatColor: string;
  pattern: string;
  size: string;
  healthTags: string[];
  behaviorTags: string[];
  earTipped: string;
  possiblyNotNeutered: boolean;
  needsMedical: boolean;
  notes: string;
  newCat: boolean;
  matchScore: number;
  aiSummary: string;
  suggestedCatIds: string[];
  rejectedCatIds: string[];
  status: string;
  createdAt: number;
  updatedAt: number;
}
export const SightingDefaults: Sighting = {
  id: "",
  catId: "",
  catName: "",
  userId: "",
  username: "",
  photoIds: [],
  seenAt: 0,
  lat: 0.0,
  lng: 0.0,
  neighborhood: "",
  locationSource: "gps",
  coatColor: "unknown",
  pattern: "unknown",
  size: "unknown",
  healthTags: [],
  behaviorTags: [],
  earTipped: "unsure",
  possiblyNotNeutered: false,
  needsMedical: false,
  notes: "",
  newCat: false,
  matchScore: 0.0,
  aiSummary: "",
  suggestedCatIds: [],
  rejectedCatIds: [],
  status: "pending",
  createdAt: 0,
  updatedAt: 0,
};
export function SightingFromDoc(id: string, data: Record<string, unknown>): Sighting {
  return {
    id: id,
    catId: typeof data.catId === "string" ? data.catId : "",
    catName: typeof data.catName === "string" ? data.catName : "",
    userId: typeof data.userId === "string" ? data.userId : "",
    username: typeof data.username === "string" ? data.username : "",
    photoIds: Array.isArray(data.photoIds) ? data.photoIds.filter((v): v is string => typeof v === "string") : [],
    seenAt: typeof data.seenAt === "number" && Number.isFinite(data.seenAt) ? data.seenAt : 0,
    lat: typeof data.lat === "number" && Number.isFinite(data.lat) ? data.lat : 0.0,
    lng: typeof data.lng === "number" && Number.isFinite(data.lng) ? data.lng : 0.0,
    neighborhood: typeof data.neighborhood === "string" ? data.neighborhood : "",
    locationSource: typeof data.locationSource === "string" ? data.locationSource : "gps",
    coatColor: typeof data.coatColor === "string" ? data.coatColor : "unknown",
    pattern: typeof data.pattern === "string" ? data.pattern : "unknown",
    size: typeof data.size === "string" ? data.size : "unknown",
    healthTags: Array.isArray(data.healthTags) ? data.healthTags.filter((v): v is string => typeof v === "string") : [],
    behaviorTags: Array.isArray(data.behaviorTags) ? data.behaviorTags.filter((v): v is string => typeof v === "string") : [],
    earTipped: typeof data.earTipped === "string" ? data.earTipped : "unsure",
    possiblyNotNeutered: typeof data.possiblyNotNeutered === "boolean" ? data.possiblyNotNeutered : false,
    needsMedical: typeof data.needsMedical === "boolean" ? data.needsMedical : false,
    notes: typeof data.notes === "string" ? data.notes : "",
    newCat: typeof data.newCat === "boolean" ? data.newCat : false,
    matchScore: typeof data.matchScore === "number" && Number.isFinite(data.matchScore) ? data.matchScore : 0.0,
    aiSummary: typeof data.aiSummary === "string" ? data.aiSummary : "",
    suggestedCatIds: Array.isArray(data.suggestedCatIds) ? data.suggestedCatIds.filter((v): v is string => typeof v === "string") : [],
    rejectedCatIds: Array.isArray(data.rejectedCatIds) ? data.rejectedCatIds.filter((v): v is string => typeof v === "string") : [],
    status: typeof data.status === "string" ? data.status : "pending",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    updatedAt: typeof data.updatedAt === "number" && Number.isFinite(data.updatedAt) ? data.updatedAt : 0,
  };
}
export function SightingToDoc(value: Sighting): Record<string, unknown> {
  return {
    catId: value.catId,
    catName: value.catName,
    userId: value.userId,
    username: value.username,
    photoIds: value.photoIds,
    seenAt: Math.round(value.seenAt),
    lat: value.lat,
    lng: value.lng,
    neighborhood: value.neighborhood,
    locationSource: value.locationSource,
    coatColor: value.coatColor,
    pattern: value.pattern,
    size: value.size,
    healthTags: value.healthTags,
    behaviorTags: value.behaviorTags,
    earTipped: value.earTipped,
    possiblyNotNeutered: value.possiblyNotNeutered,
    needsMedical: value.needsMedical,
    notes: value.notes,
    newCat: value.newCat,
    matchScore: value.matchScore,
    aiSummary: value.aiSummary,
    suggestedCatIds: value.suggestedCatIds,
    rejectedCatIds: value.rejectedCatIds,
    status: value.status,
    createdAt: Math.round(value.createdAt),
    updatedAt: Math.round(value.updatedAt),
  };
}
export interface PrivateLocation {
  id: string;
  ownerId: string;
  lat: number;
  lng: number;
  accuracy: number;
}
export const PrivateLocationDefaults: PrivateLocation = {
  id: "",
  ownerId: "",
  lat: 0.0,
  lng: 0.0,
  accuracy: 0.0,
};
export function PrivateLocationFromDoc(id: string, data: Record<string, unknown>): PrivateLocation {
  return {
    id: id,
    ownerId: typeof data.ownerId === "string" ? data.ownerId : "",
    lat: typeof data.lat === "number" && Number.isFinite(data.lat) ? data.lat : 0.0,
    lng: typeof data.lng === "number" && Number.isFinite(data.lng) ? data.lng : 0.0,
    accuracy: typeof data.accuracy === "number" && Number.isFinite(data.accuracy) ? data.accuracy : 0.0,
  };
}
export function PrivateLocationToDoc(value: PrivateLocation): Record<string, unknown> {
  return {
    ownerId: value.ownerId,
    lat: value.lat,
    lng: value.lng,
    accuracy: value.accuracy,
  };
}
export interface MedicalRecord {
  id: string;
  catId: string;
  type: string;
  note: string;
  byUid: string;
  byName: string;
  at: number;
}
export const MedicalRecordDefaults: MedicalRecord = {
  id: "",
  catId: "",
  type: "note",
  note: "",
  byUid: "",
  byName: "",
  at: 0,
};
export function MedicalRecordFromDoc(id: string, data: Record<string, unknown>): MedicalRecord {
  return {
    id: id,
    catId: typeof data.catId === "string" ? data.catId : "",
    type: typeof data.type === "string" ? data.type : "note",
    note: typeof data.note === "string" ? data.note : "",
    byUid: typeof data.byUid === "string" ? data.byUid : "",
    byName: typeof data.byName === "string" ? data.byName : "",
    at: typeof data.at === "number" && Number.isFinite(data.at) ? data.at : 0,
  };
}
export function MedicalRecordToDoc(value: MedicalRecord): Record<string, unknown> {
  return {
    catId: value.catId,
    type: value.type,
    note: value.note,
    byUid: value.byUid,
    byName: value.byName,
    at: Math.round(value.at),
  };
}
export interface RescueUpdate {
  status: string;
  note: string;
  byUid: string;
  byName: string;
  at: number;
}
export const RescueUpdateDefaults: RescueUpdate = {
  status: "",
  note: "",
  byUid: "",
  byName: "",
  at: 0,
};
export function RescueUpdateFromDoc(id: string, data: Record<string, unknown>): RescueUpdate {
  return {
    status: typeof data.status === "string" ? data.status : "",
    note: typeof data.note === "string" ? data.note : "",
    byUid: typeof data.byUid === "string" ? data.byUid : "",
    byName: typeof data.byName === "string" ? data.byName : "",
    at: typeof data.at === "number" && Number.isFinite(data.at) ? data.at : 0,
  };
}
export function RescueUpdateToDoc(value: RescueUpdate): Record<string, unknown> {
  return {
    status: value.status,
    note: value.note,
    byUid: value.byUid,
    byName: value.byName,
    at: Math.round(value.at),
  };
}
export interface RescueReport {
  id: string;
  type: string;
  urgency: string;
  description: string;
  landmark: string;
  photoIds: string[];
  lat: number;
  lng: number;
  neighborhood: string;
  catId: string;
  catName: string;
  reporterId: string;
  reporterName: string;
  status: string;
  assignedTo: string;
  assignedName: string;
  updates: RescueUpdate[];
  createdAt: number;
  updatedAt: number;
}
export const RescueReportDefaults: RescueReport = {
  id: "",
  type: "injured",
  urgency: "medium",
  description: "",
  landmark: "",
  photoIds: [],
  lat: 0.0,
  lng: 0.0,
  neighborhood: "",
  catId: "",
  catName: "",
  reporterId: "",
  reporterName: "",
  status: "submitted",
  assignedTo: "",
  assignedName: "",
  updates: [],
  createdAt: 0,
  updatedAt: 0,
};
export function RescueReportFromDoc(id: string, data: Record<string, unknown>): RescueReport {
  return {
    id: id,
    type: typeof data.type === "string" ? data.type : "injured",
    urgency: typeof data.urgency === "string" ? data.urgency : "medium",
    description: typeof data.description === "string" ? data.description : "",
    landmark: typeof data.landmark === "string" ? data.landmark : "",
    photoIds: Array.isArray(data.photoIds) ? data.photoIds.filter((v): v is string => typeof v === "string") : [],
    lat: typeof data.lat === "number" && Number.isFinite(data.lat) ? data.lat : 0.0,
    lng: typeof data.lng === "number" && Number.isFinite(data.lng) ? data.lng : 0.0,
    neighborhood: typeof data.neighborhood === "string" ? data.neighborhood : "",
    catId: typeof data.catId === "string" ? data.catId : "",
    catName: typeof data.catName === "string" ? data.catName : "",
    reporterId: typeof data.reporterId === "string" ? data.reporterId : "",
    reporterName: typeof data.reporterName === "string" ? data.reporterName : "",
    status: typeof data.status === "string" ? data.status : "submitted",
    assignedTo: typeof data.assignedTo === "string" ? data.assignedTo : "",
    assignedName: typeof data.assignedName === "string" ? data.assignedName : "",
    updates: Array.isArray(data.updates) ? data.updates.filter(v => v && typeof v === "object" && !Array.isArray(v)).map(v => RescueUpdateFromDoc("", v)) : [],
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    updatedAt: typeof data.updatedAt === "number" && Number.isFinite(data.updatedAt) ? data.updatedAt : 0,
  };
}
export function RescueReportToDoc(value: RescueReport): Record<string, unknown> {
  return {
    type: value.type,
    urgency: value.urgency,
    description: value.description,
    landmark: value.landmark,
    photoIds: value.photoIds,
    lat: value.lat,
    lng: value.lng,
    neighborhood: value.neighborhood,
    catId: value.catId,
    catName: value.catName,
    reporterId: value.reporterId,
    reporterName: value.reporterName,
    status: value.status,
    assignedTo: value.assignedTo,
    assignedName: value.assignedName,
    updates: value.updates,
    createdAt: Math.round(value.createdAt),
    updatedAt: Math.round(value.updatedAt),
  };
}
export interface FeedingStation {
  id: string;
  name: string;
  description: string;
  operatorName: string;
  schedule: string;
  foodType: string;
  hasWater: boolean;
  lat: number;
  lng: number;
  neighborhood: string;
  verified: boolean;
  active: boolean;
  sample: boolean;
  checkinCount: number;
  lastCheckinAt: number;
  lastCheckinBy: string;
  createdAt: number;
  updatedAt: number;
}
export const FeedingStationDefaults: FeedingStation = {
  id: "",
  name: "",
  description: "",
  operatorName: "",
  schedule: "",
  foodType: "",
  hasWater: true,
  lat: 0.0,
  lng: 0.0,
  neighborhood: "",
  verified: true,
  active: true,
  sample: false,
  checkinCount: 0,
  lastCheckinAt: 0,
  lastCheckinBy: "",
  createdAt: 0,
  updatedAt: 0,
};
export function FeedingStationFromDoc(id: string, data: Record<string, unknown>): FeedingStation {
  return {
    id: id,
    name: typeof data.name === "string" ? data.name : "",
    description: typeof data.description === "string" ? data.description : "",
    operatorName: typeof data.operatorName === "string" ? data.operatorName : "",
    schedule: typeof data.schedule === "string" ? data.schedule : "",
    foodType: typeof data.foodType === "string" ? data.foodType : "",
    hasWater: typeof data.hasWater === "boolean" ? data.hasWater : true,
    lat: typeof data.lat === "number" && Number.isFinite(data.lat) ? data.lat : 0.0,
    lng: typeof data.lng === "number" && Number.isFinite(data.lng) ? data.lng : 0.0,
    neighborhood: typeof data.neighborhood === "string" ? data.neighborhood : "",
    verified: typeof data.verified === "boolean" ? data.verified : true,
    active: typeof data.active === "boolean" ? data.active : true,
    sample: typeof data.sample === "boolean" ? data.sample : false,
    checkinCount: typeof data.checkinCount === "number" && Number.isFinite(data.checkinCount) ? data.checkinCount : 0,
    lastCheckinAt: typeof data.lastCheckinAt === "number" && Number.isFinite(data.lastCheckinAt) ? data.lastCheckinAt : 0,
    lastCheckinBy: typeof data.lastCheckinBy === "string" ? data.lastCheckinBy : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    updatedAt: typeof data.updatedAt === "number" && Number.isFinite(data.updatedAt) ? data.updatedAt : 0,
  };
}
export function FeedingStationToDoc(value: FeedingStation): Record<string, unknown> {
  return {
    name: value.name,
    description: value.description,
    operatorName: value.operatorName,
    schedule: value.schedule,
    foodType: value.foodType,
    hasWater: value.hasWater,
    lat: value.lat,
    lng: value.lng,
    neighborhood: value.neighborhood,
    verified: value.verified,
    active: value.active,
    sample: value.sample,
    checkinCount: Math.round(value.checkinCount),
    lastCheckinAt: Math.round(value.lastCheckinAt),
    lastCheckinBy: value.lastCheckinBy,
    createdAt: Math.round(value.createdAt),
    updatedAt: Math.round(value.updatedAt),
  };
}
export interface StationCheckin {
  id: string;
  stationId: string;
  stationName: string;
  userId: string;
  username: string;
  catsSeen: number;
  catIds: string[];
  foodLevel: string;
  waterLevel: string;
  note: string;
  at: number;
}
export const StationCheckinDefaults: StationCheckin = {
  id: "",
  stationId: "",
  stationName: "",
  userId: "",
  username: "",
  catsSeen: 0,
  catIds: [],
  foodLevel: "unknown",
  waterLevel: "unknown",
  note: "",
  at: 0,
};
export function StationCheckinFromDoc(id: string, data: Record<string, unknown>): StationCheckin {
  return {
    id: id,
    stationId: typeof data.stationId === "string" ? data.stationId : "",
    stationName: typeof data.stationName === "string" ? data.stationName : "",
    userId: typeof data.userId === "string" ? data.userId : "",
    username: typeof data.username === "string" ? data.username : "",
    catsSeen: typeof data.catsSeen === "number" && Number.isFinite(data.catsSeen) ? data.catsSeen : 0,
    catIds: Array.isArray(data.catIds) ? data.catIds.filter((v): v is string => typeof v === "string") : [],
    foodLevel: typeof data.foodLevel === "string" ? data.foodLevel : "unknown",
    waterLevel: typeof data.waterLevel === "string" ? data.waterLevel : "unknown",
    note: typeof data.note === "string" ? data.note : "",
    at: typeof data.at === "number" && Number.isFinite(data.at) ? data.at : 0,
  };
}
export function StationCheckinToDoc(value: StationCheckin): Record<string, unknown> {
  return {
    stationId: value.stationId,
    stationName: value.stationName,
    userId: value.userId,
    username: value.username,
    catsSeen: Math.round(value.catsSeen),
    catIds: value.catIds,
    foodLevel: value.foodLevel,
    waterLevel: value.waterLevel,
    note: value.note,
    at: Math.round(value.at),
  };
}
export interface MaintenanceReport {
  id: string;
  stationId: string;
  stationName: string;
  userId: string;
  username: string;
  issue: string;
  description: string;
  status: string;
  resolutionNote: string;
  resolvedBy: string;
  createdAt: number;
  updatedAt: number;
}
export const MaintenanceReportDefaults: MaintenanceReport = {
  id: "",
  stationId: "",
  stationName: "",
  userId: "",
  username: "",
  issue: "other",
  description: "",
  status: "open",
  resolutionNote: "",
  resolvedBy: "",
  createdAt: 0,
  updatedAt: 0,
};
export function MaintenanceReportFromDoc(id: string, data: Record<string, unknown>): MaintenanceReport {
  return {
    id: id,
    stationId: typeof data.stationId === "string" ? data.stationId : "",
    stationName: typeof data.stationName === "string" ? data.stationName : "",
    userId: typeof data.userId === "string" ? data.userId : "",
    username: typeof data.username === "string" ? data.username : "",
    issue: typeof data.issue === "string" ? data.issue : "other",
    description: typeof data.description === "string" ? data.description : "",
    status: typeof data.status === "string" ? data.status : "open",
    resolutionNote: typeof data.resolutionNote === "string" ? data.resolutionNote : "",
    resolvedBy: typeof data.resolvedBy === "string" ? data.resolvedBy : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    updatedAt: typeof data.updatedAt === "number" && Number.isFinite(data.updatedAt) ? data.updatedAt : 0,
  };
}
export function MaintenanceReportToDoc(value: MaintenanceReport): Record<string, unknown> {
  return {
    stationId: value.stationId,
    stationName: value.stationName,
    userId: value.userId,
    username: value.username,
    issue: value.issue,
    description: value.description,
    status: value.status,
    resolutionNote: value.resolutionNote,
    resolvedBy: value.resolvedBy,
    createdAt: Math.round(value.createdAt),
    updatedAt: Math.round(value.updatedAt),
  };
}
export interface MergeRequest {
  id: string;
  primaryCatId: string;
  primaryName: string;
  duplicateCatId: string;
  duplicateName: string;
  requestedBy: string;
  requestedByName: string;
  reason: string;
  similarity: number;
  status: string;
  reviewedBy: string;
  reviewedByName: string;
  reviewNote: string;
  createdAt: number;
  reviewedAt: number;
}
export const MergeRequestDefaults: MergeRequest = {
  id: "",
  primaryCatId: "",
  primaryName: "",
  duplicateCatId: "",
  duplicateName: "",
  requestedBy: "",
  requestedByName: "",
  reason: "",
  similarity: 0.0,
  status: "pending",
  reviewedBy: "",
  reviewedByName: "",
  reviewNote: "",
  createdAt: 0,
  reviewedAt: 0,
};
export function MergeRequestFromDoc(id: string, data: Record<string, unknown>): MergeRequest {
  return {
    id: id,
    primaryCatId: typeof data.primaryCatId === "string" ? data.primaryCatId : "",
    primaryName: typeof data.primaryName === "string" ? data.primaryName : "",
    duplicateCatId: typeof data.duplicateCatId === "string" ? data.duplicateCatId : "",
    duplicateName: typeof data.duplicateName === "string" ? data.duplicateName : "",
    requestedBy: typeof data.requestedBy === "string" ? data.requestedBy : "",
    requestedByName: typeof data.requestedByName === "string" ? data.requestedByName : "",
    reason: typeof data.reason === "string" ? data.reason : "",
    similarity: typeof data.similarity === "number" && Number.isFinite(data.similarity) ? data.similarity : 0.0,
    status: typeof data.status === "string" ? data.status : "pending",
    reviewedBy: typeof data.reviewedBy === "string" ? data.reviewedBy : "",
    reviewedByName: typeof data.reviewedByName === "string" ? data.reviewedByName : "",
    reviewNote: typeof data.reviewNote === "string" ? data.reviewNote : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
    reviewedAt: typeof data.reviewedAt === "number" && Number.isFinite(data.reviewedAt) ? data.reviewedAt : 0,
  };
}
export function MergeRequestToDoc(value: MergeRequest): Record<string, unknown> {
  return {
    primaryCatId: value.primaryCatId,
    primaryName: value.primaryName,
    duplicateCatId: value.duplicateCatId,
    duplicateName: value.duplicateName,
    requestedBy: value.requestedBy,
    requestedByName: value.requestedByName,
    reason: value.reason,
    similarity: value.similarity,
    status: value.status,
    reviewedBy: value.reviewedBy,
    reviewedByName: value.reviewedByName,
    reviewNote: value.reviewNote,
    createdAt: Math.round(value.createdAt),
    reviewedAt: Math.round(value.reviewedAt),
  };
}
export interface ContentReport {
  id: string;
  targetType: string;
  targetId: string;
  targetLabel: string;
  reason: string;
  details: string;
  reporterId: string;
  reporterName: string;
  status: string;
  resolution: string;
  resolvedBy: string;
  createdAt: number;
}
export const ContentReportDefaults: ContentReport = {
  id: "",
  targetType: "",
  targetId: "",
  targetLabel: "",
  reason: "other",
  details: "",
  reporterId: "",
  reporterName: "",
  status: "open",
  resolution: "",
  resolvedBy: "",
  createdAt: 0,
};
export function ContentReportFromDoc(id: string, data: Record<string, unknown>): ContentReport {
  return {
    id: id,
    targetType: typeof data.targetType === "string" ? data.targetType : "",
    targetId: typeof data.targetId === "string" ? data.targetId : "",
    targetLabel: typeof data.targetLabel === "string" ? data.targetLabel : "",
    reason: typeof data.reason === "string" ? data.reason : "other",
    details: typeof data.details === "string" ? data.details : "",
    reporterId: typeof data.reporterId === "string" ? data.reporterId : "",
    reporterName: typeof data.reporterName === "string" ? data.reporterName : "",
    status: typeof data.status === "string" ? data.status : "open",
    resolution: typeof data.resolution === "string" ? data.resolution : "",
    resolvedBy: typeof data.resolvedBy === "string" ? data.resolvedBy : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
  };
}
export function ContentReportToDoc(value: ContentReport): Record<string, unknown> {
  return {
    targetType: value.targetType,
    targetId: value.targetId,
    targetLabel: value.targetLabel,
    reason: value.reason,
    details: value.details,
    reporterId: value.reporterId,
    reporterName: value.reporterName,
    status: value.status,
    resolution: value.resolution,
    resolvedBy: value.resolvedBy,
    createdAt: Math.round(value.createdAt),
  };
}
export interface AppNotification {
  id: string;
  type: string;
  params: Record<string, string>;
  route: string;
  fromUid: string;
  read: boolean;
  createdAt: number;
}
export const AppNotificationDefaults: AppNotification = {
  id: "",
  type: "",
  params: {},
  route: "",
  fromUid: "",
  read: false,
  createdAt: 0,
};
export function AppNotificationFromDoc(id: string, data: Record<string, unknown>): AppNotification {
  return {
    id: id,
    type: typeof data.type === "string" ? data.type : "",
    params: data.params && typeof data.params === "object" && !Array.isArray(data.params) ? Object.fromEntries(Object.entries(data.params).filter(([, v]) => typeof v === "string")) as Record<string, string> : {},
    route: typeof data.route === "string" ? data.route : "",
    fromUid: typeof data.fromUid === "string" ? data.fromUid : "",
    read: typeof data.read === "boolean" ? data.read : false,
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
  };
}
export function AppNotificationToDoc(value: AppNotification): Record<string, unknown> {
  return {
    type: value.type,
    params: value.params,
    route: value.route,
    fromUid: value.fromUid,
    read: value.read,
    createdAt: Math.round(value.createdAt),
  };
}
export interface ModAction {
  id: string;
  moderatorId: string;
  moderatorName: string;
  action: string;
  targetType: string;
  targetId: string;
  targetLabel: string;
  note: string;
  at: number;
}
export const ModActionDefaults: ModAction = {
  id: "",
  moderatorId: "",
  moderatorName: "",
  action: "",
  targetType: "",
  targetId: "",
  targetLabel: "",
  note: "",
  at: 0,
};
export function ModActionFromDoc(id: string, data: Record<string, unknown>): ModAction {
  return {
    id: id,
    moderatorId: typeof data.moderatorId === "string" ? data.moderatorId : "",
    moderatorName: typeof data.moderatorName === "string" ? data.moderatorName : "",
    action: typeof data.action === "string" ? data.action : "",
    targetType: typeof data.targetType === "string" ? data.targetType : "",
    targetId: typeof data.targetId === "string" ? data.targetId : "",
    targetLabel: typeof data.targetLabel === "string" ? data.targetLabel : "",
    note: typeof data.note === "string" ? data.note : "",
    at: typeof data.at === "number" && Number.isFinite(data.at) ? data.at : 0,
  };
}
export function ModActionToDoc(value: ModAction): Record<string, unknown> {
  return {
    moderatorId: value.moderatorId,
    moderatorName: value.moderatorName,
    action: value.action,
    targetType: value.targetType,
    targetId: value.targetId,
    targetLabel: value.targetLabel,
    note: value.note,
    at: Math.round(value.at),
  };
}
export interface VolunteerApplication {
  id: string;
  userId: string;
  username: string;
  motivation: string;
  experience: string;
  area: string;
  status: string;
  reviewedBy: string;
  createdAt: number;
}
export const VolunteerApplicationDefaults: VolunteerApplication = {
  id: "",
  userId: "",
  username: "",
  motivation: "",
  experience: "",
  area: "",
  status: "pending",
  reviewedBy: "",
  createdAt: 0,
};
export function VolunteerApplicationFromDoc(id: string, data: Record<string, unknown>): VolunteerApplication {
  return {
    id: id,
    userId: typeof data.userId === "string" ? data.userId : "",
    username: typeof data.username === "string" ? data.username : "",
    motivation: typeof data.motivation === "string" ? data.motivation : "",
    experience: typeof data.experience === "string" ? data.experience : "",
    area: typeof data.area === "string" ? data.area : "",
    status: typeof data.status === "string" ? data.status : "pending",
    reviewedBy: typeof data.reviewedBy === "string" ? data.reviewedBy : "",
    createdAt: typeof data.createdAt === "number" && Number.isFinite(data.createdAt) ? data.createdAt : 0,
  };
}
export function VolunteerApplicationToDoc(value: VolunteerApplication): Record<string, unknown> {
  return {
    userId: value.userId,
    username: value.username,
    motivation: value.motivation,
    experience: value.experience,
    area: value.area,
    status: value.status,
    reviewedBy: value.reviewedBy,
    createdAt: Math.round(value.createdAt),
  };
}
