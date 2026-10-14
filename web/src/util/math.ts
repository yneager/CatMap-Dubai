/** Kotlin kotlin.math.round uses ties to even; Java Math.round uses ties up. */
export function roundEven(value: number) {
  const floor = Math.floor(value);
  return value - floor === 0.5 ? (floor % 2 === 0 ? floor : floor + 1) : Math.round(value);
}
