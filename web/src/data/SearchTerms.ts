export const SearchTerms = {
  words: (...texts: string[]) =>
    [
      ...new Set(
        texts.flatMap((t) => t.toLowerCase().split(/[^\p{L}\p{N}]+/u)).filter((w) => w.length >= 2),
      ),
    ].slice(0, 80),
  of(words: string[]): string[] {
    const out = new Set(words);
    words.forEach((w, i) => {
      if (i < 25 || i >= words.length - 6)
        for (let k = 3; k <= Math.min(w.length - 1, 7); k++) out.add(w.slice(0, k));
    });
    return [...out].slice(0, 200);
  },
  query: (word: string) => [...new Set([word, word.slice(0, 7)])],
  matches: (word: string, words: string[]) => words.some((w) => w.startsWith(word)),
};
