// Generated from Android Kotlin models. Run npm run models:generate.
import type { Timestamp } from 'firebase/firestore';
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
};
export function PostFromDoc(id: string, data: Record<string, unknown>): Post {
  return Object.assign(Object.fromEntries(Object.entries(PostDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as Post;
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
  return Object.assign(Object.fromEntries(Object.entries(PostMatchDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as PostMatch;
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
  return Object.assign(Object.fromEntries(Object.entries(PostUpdateDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as PostUpdate;
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
  return Object.assign(Object.fromEntries(Object.entries(AdoptionRequestDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as AdoptionRequest;
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
  return Object.assign(Object.fromEntries(Object.entries(SitterProfileDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { uid: id }) as unknown as SitterProfile;
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
  return Object.assign(Object.fromEntries(Object.entries(BookingDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as Booking;
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
  return Object.assign(Object.fromEntries(Object.entries(ReviewDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as Review;
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
  return Object.assign(Object.fromEntries(Object.entries(ChatDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as Chat;
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
  return Object.assign(Object.fromEntries(Object.entries(MessageDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as Message;
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
  return Object.assign(Object.fromEntries(Object.entries(AreaFollowDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { uid: id }) as unknown as AreaFollow;
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
  return Object.assign(Object.fromEntries(Object.entries(FavoriteDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as Favorite;
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
  return Object.assign(Object.fromEntries(Object.entries(UserProfileDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { uid: id }) as unknown as UserProfile;
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
};
export function CatFromDoc(id: string, data: Record<string, unknown>): Cat {
  return Object.assign(Object.fromEntries(Object.entries(CatDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as Cat;
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
  return Object.assign(Object.fromEntries(Object.entries(SightingDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as Sighting;
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
  return Object.assign(Object.fromEntries(Object.entries(PrivateLocationDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as PrivateLocation;
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
  return Object.assign(Object.fromEntries(Object.entries(MedicalRecordDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as MedicalRecord;
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
  return Object.assign(Object.fromEntries(Object.entries(RescueUpdateDefaults).map(([key, value]) => [key, key in data ? data[key] : value]))) as unknown as RescueUpdate;
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
  return Object.assign(Object.fromEntries(Object.entries(RescueReportDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as RescueReport;
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
  return Object.assign(Object.fromEntries(Object.entries(FeedingStationDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as FeedingStation;
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
  return Object.assign(Object.fromEntries(Object.entries(StationCheckinDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as StationCheckin;
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
  return Object.assign(Object.fromEntries(Object.entries(MaintenanceReportDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as MaintenanceReport;
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
  return Object.assign(Object.fromEntries(Object.entries(MergeRequestDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as MergeRequest;
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
  return Object.assign(Object.fromEntries(Object.entries(ContentReportDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as ContentReport;
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
  return Object.assign(Object.fromEntries(Object.entries(AppNotificationDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as AppNotification;
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
  return Object.assign(Object.fromEntries(Object.entries(ModActionDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as ModAction;
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
  return Object.assign(Object.fromEntries(Object.entries(VolunteerApplicationDefaults).map(([key, value]) => [key, key in data ? data[key] : value])), { id: id }) as unknown as VolunteerApplication;
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
