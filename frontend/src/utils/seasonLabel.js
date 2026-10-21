export function seasonLabel(season, format) {
  if (season == null) return 'All seasons';
  if (format === 'FC') return `Season ${season}/${season + 1}`;
  return `Season ${season}`;
}

export function toStoredSeason(season, format) {
  if (season == null || format !== 'FC') return season;
  return season % 2 === 0 ? season - 1 : season;
}

export function seasonOptions(maxSeason, format) {
  if (format === 'FC') {
    const seasons = [];
    for (let s = 1; s <= maxSeason; s += 2) seasons.push(s);
    return seasons.reverse();
  }
  return Array.from({ length: maxSeason }, (_, i) => maxSeason - i);
}
