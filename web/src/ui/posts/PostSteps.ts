export const postSteps = ['TYPE', 'PLACE', 'PHOTOS', 'DETAILS', 'CONTACT', 'REVIEW'] as const;
export type PostStep = (typeof postSteps)[number];
const oldOrder: PostStep[] = ['TYPE', 'PHOTOS', 'DETAILS', 'PLACE', 'CONTACT', 'REVIEW'];
export const stepLabels: Record<PostStep, string> = {
  TYPE: 'step_type',
  PLACE: 'step_place',
  PHOTOS: 'step_photos',
  DETAILS: 'step_details',
  CONTACT: 'step_contact',
  REVIEW: 'step_review',
};
export const visibleSteps = (category: string) =>
  postSteps.filter((step) => step !== 'CONTACT' || category !== 'street');
export function restoredStep(fields: { stepName?: unknown; step?: unknown }): PostStep {
  if (postSteps.includes(fields.stepName as PostStep)) return fields.stepName as PostStep;
  const position =
    typeof fields.step === 'number'
      ? fields.step
      : typeof fields.step === 'string' && /^\d+$/.test(fields.step)
        ? Number(fields.step)
        : -1;
  return oldOrder[position] ?? 'TYPE';
}
export function stepAfter(step: PostStep, category: string, forward: boolean): PostStep {
  const steps = visibleSteps(category);
  let at = steps.indexOf(step);
  if (at < 0) at = steps.findLastIndex((item) => postSteps.indexOf(item) < postSteps.indexOf(step));
  return steps[at + (forward ? 1 : -1)] ?? step;
}
