type ActivityCategoryInput = {
  primaryInterest?: string
  category?: string
  subcategory?: string
  name?: string
}

export type DisplayActivityCategory = 'Kultur' | 'Geschichte' | 'Natur' | 'Food' | 'Shopping' | 'Nightlife' | 'Sport'

const normalizeText = (value: unknown) => String(value || '')
  .normalize('NFD')
  .replace(/\p{M}/gu, '')
  .toLowerCase()

export const displayCategoryForActivity = (activity?: ActivityCategoryInput | null): DisplayActivityCategory => {
  const primaryInterest = String(activity?.primaryInterest || '').toUpperCase()
  if (primaryInterest === 'NIGHTLIFE') return 'Nightlife'
  if (primaryInterest === 'FOOD') return 'Food'
  if (primaryInterest === 'NATURE') return 'Natur'
  if (primaryInterest === 'SHOPPING') return 'Shopping'
  if (primaryInterest === 'CULTURE') return 'Kultur'
  if (primaryInterest === 'SIGHTSEEING') return 'Geschichte'
  if (primaryInterest === 'HISTORY') return 'Geschichte'
  if (primaryInterest === 'SPORT') return 'Sport'

  const value = normalizeText(`${activity?.category || ''} ${activity?.subcategory || ''} ${activity?.name || ''}`)
  if (/night|club|bar|pub|nacht/.test(value)) return 'Nightlife'
  if (/food|essen|restaurant|cafe|cafes|market|markt|catering/.test(value)) return 'Food'
  if (/park|natur|garden|garten|forest|wald|beach|strand/.test(value)) return 'Natur'
  if (/shop|shopping|commercial|mall|markt|markte/.test(value)) return 'Shopping'
  if (/sport|stadium|fitness/.test(value)) return 'Sport'
  if (/heritage|historic|historisch|monument|castle|schloss|geschichte/.test(value)) return 'Geschichte'
  return 'Kultur'
}

export const fallbackImageCategoryForActivity = (activity?: ActivityCategoryInput | null) => ({
  Kultur: 'culture',
  Geschichte: 'history',
  Natur: 'nature',
  Food: 'food',
  Shopping: 'shopping',
  Nightlife: 'nightlife',
  Sport: 'sport'
}[displayCategoryForActivity(activity)])
