export const QuietHours = {
  contains: (minute: number, start: number, end: number) => {
    if ([minute, start, end].some((v) => v < 0 || v > 1439 || !Number.isInteger(v)))
      throw new RangeError('Invalid minute');
    return start === end
      ? true
      : start < end
        ? minute >= start && minute < end
        : minute >= start || minute < end;
  },
};
