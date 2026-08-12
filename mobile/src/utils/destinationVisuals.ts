const knownCityImages: Record<string, string> = {
  amsterdam: 'https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=900&q=90',
  copenhagen: 'https://images.unsplash.com/photo-1513622470522-26c3c8a854bc?auto=format&fit=crop&w=900&q=90',
  berlin: 'https://images.unsplash.com/photo-1560969184-10fe8719e047?auto=format&fit=crop&w=900&q=90',
  paris: 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=900&q=90',
  rome: 'https://images.unsplash.com/photo-1552832230-c0197dd311b5?auto=format&fit=crop&w=900&q=90',
  barcelona: 'https://images.unsplash.com/photo-1583422409516-2895a77efded?auto=format&fit=crop&w=900&q=90',
  istanbul: 'https://images.unsplash.com/photo-1524231757912-21f4fe3a7200?auto=format&fit=crop&w=900&q=90',
  london: 'https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?auto=format&fit=crop&w=900&q=90',
  lisbon: 'https://images.unsplash.com/photo-1508685096489-7aacd43bd3b1?auto=format&fit=crop&w=900&q=90',
  tokyo: 'https://images.unsplash.com/photo-1503899036084-c55cdd92da26?auto=format&fit=crop&w=900&q=90',
  bursa: 'https://commons.wikimedia.org/wiki/Special:FilePath/Bursa_Turkey_panorama.jpg?width=900',
  edirne: 'https://commons.wikimedia.org/wiki/Special:FilePath/Selimiye_Mosque_Edirne.jpg?width=900',
  canakkale: citySearchImage('Canakkale Turkey'),
  'çanakkale': citySearchImage('Canakkale Turkey'),
  eskisehir: 'https://commons.wikimedia.org/wiki/Special:FilePath/Porsuk_River_Eskisehir.jpg?width=900',
  'eskişehir': 'https://commons.wikimedia.org/wiki/Special:FilePath/Porsuk_River_Eskisehir.jpg?width=900',
  ankara: 'https://commons.wikimedia.org/wiki/Special:FilePath/An%C4%B1tkabir%2C_Ankara.jpg?width=900',
  izmir: 'https://commons.wikimedia.org/wiki/Special:FilePath/Izmir_Kordon.jpg?width=900',
  'i̇zmir': 'https://commons.wikimedia.org/wiki/Special:FilePath/Izmir_Kordon.jpg?width=900',
  antalya: 'https://commons.wikimedia.org/wiki/Special:FilePath/Kaleici_Antalya.jpg?width=900',
  edinburgh: 'https://commons.wikimedia.org/wiki/Special:FilePath/Edinburgh_Castle_31_July_2011.jpg?width=900',
  edinburg: 'https://commons.wikimedia.org/wiki/Special:FilePath/Edinburgh_Castle_31_July_2011.jpg?width=900',
};

const cityBackupImages: Record<string, string> = {
  ankara: citySearchImage('Ankara'),
  antalya: citySearchImage('Antalya'),
  bursa: citySearchImage('Bursa Turkey'),
  canakkale: citySearchImage('Canakkale Turkey'),
  'çanakkale': citySearchImage('Canakkale Turkey'),
  edirne: citySearchImage('Edirne'),
  eskisehir: citySearchImage('Eskisehir'),
  'eskişehir': citySearchImage('Eskisehir'),
  izmir: citySearchImage('Izmir'),
  'i̇zmir': citySearchImage('Izmir'),
};

const categoryImages: Record<string, string[]> = {
  food: [
    'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1533777857889-4be7c70b33f7?auto=format&fit=crop&w=700&q=85',
  ],
  coffee: [
    'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1511920170033-f8396924c348?auto=format&fit=crop&w=700&q=85',
  ],
  culture: [
    'https://images.unsplash.com/photo-1518998053901-5348d3961a04?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1545987796-200677ee1011?auto=format&fit=crop&w=700&q=85',
  ],
  walking: [
    'https://images.unsplash.com/photo-1584003564911-a7a321c84e1c?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1525968902-070804c45d6b?auto=format&fit=crop&w=700&q=85',
  ],
};

export function cityImage(destination?: string) {
  const city = destination?.trim();
  if (!city) {
    return knownCityImages.amsterdam;
  }
  const known = knownCityImages[normalizeCity(city)];
  if (known) {
    return known;
  }
  return `https://commons.wikimedia.org/wiki/Special:FilePath/${encodeURIComponent(city.replace(/\s+/g, '_'))}.jpg?width=900`;
}

export function cityBackupImage(destination?: string) {
  const normalized = normalizeCity(destination ?? '');
  const known = cityBackupImages[normalized];
  if (known) {
    return known;
  }
  const city = destination?.trim() || 'city';
  return citySearchImage(city);
}

export function placeImage(destination: string | undefined, category: string | undefined, seed: string) {
  const normalizedCategory = (category ?? '').toLowerCase();
  if (normalizedCategory.includes('food') || normalizedCategory.includes('restaurant')) {
    return pick(categoryImages.food, seed);
  }
  if (normalizedCategory.includes('coffee') || normalizedCategory.includes('cafe')) {
    return pick(categoryImages.coffee, seed);
  }
  if (normalizedCategory.includes('culture') || normalizedCategory.includes('museum')) {
    return pick(categoryImages.culture, seed);
  }
  if (normalizedCategory.includes('walking') || normalizedCategory.includes('free')) {
    return cityImage(destination);
  }
  return cityImage(destination);
}

export function cityMeta(destination?: string) {
  const normalized = normalizeCity(destination ?? '');
  if (normalized === 'bursa') return 'Ottoman heritage - bazaars - mountain views';
  if (normalized === 'canakkale') return 'Waterfront - history - local food';
  if (normalized === 'eskisehir' || normalized === 'eskişehir') return 'Porsuk river - old town - cafes';
  if (normalized === 'ankara') return 'Museums - republic history - cafes';
  if (normalized === 'izmir' || normalized === 'i̇zmir') return 'Seaside walks - markets - local food';
  if (normalized === 'antalya') return 'Old town - beaches - historic walks';
  if (normalized === 'edirne') return 'Ottoman heritage - river walks - local food';
  if (normalized === 'edinburgh' || normalized === 'edinburg') return 'Castle views - old town - pubs';
  return 'Provider search - local picks - flexible planning';
}

export function cityCoordinates(destination?: string) {
  const normalized = normalizeCity(destination ?? '');
  const coordinates: Record<string, { latitude: number; longitude: number }> = {
    amsterdam: { latitude: 52.3676, longitude: 4.9041 },
    copenhagen: { latitude: 55.6761, longitude: 12.5683 },
    berlin: { latitude: 52.52, longitude: 13.405 },
    paris: { latitude: 48.8566, longitude: 2.3522 },
    rome: { latitude: 41.9028, longitude: 12.4964 },
    barcelona: { latitude: 41.3874, longitude: 2.1686 },
    istanbul: { latitude: 41.0082, longitude: 28.9784 },
    london: { latitude: 51.5072, longitude: -0.1276 },
    lisbon: { latitude: 38.7223, longitude: -9.1393 },
    tokyo: { latitude: 35.6762, longitude: 139.6503 },
    bursa: { latitude: 40.1828, longitude: 29.0663 },
    canakkale: { latitude: 40.1553, longitude: 26.4142 },
    edirne: { latitude: 41.6771, longitude: 26.5557 },
    eskisehir: { latitude: 39.7667, longitude: 30.5256 },
    ankara: { latitude: 39.9334, longitude: 32.8597 },
    izmir: { latitude: 38.4237, longitude: 27.1428 },
    antalya: { latitude: 36.8969, longitude: 30.7133 },
    edinburgh: { latitude: 55.9533, longitude: -3.1883 },
    edinburg: { latitude: 55.9533, longitude: -3.1883 },
  };

  return coordinates[normalized] ?? { latitude: 52.3676, longitude: 4.9041 };
}

function normalizeCity(value: string) {
  return value
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/ı/g, 'i')
    .replace(/ş/g, 's')
    .replace(/ğ/g, 'g')
    .replace(/ü/g, 'u')
    .replace(/ö/g, 'o')
    .replace(/ç/g, 'c');
}

function citySearchImage(city: string) {
  return `https://loremflickr.com/900/600/${encodeURIComponent(`${city},city,landmark`)}`;
}

function pick(images: string[], seed: string) {
  return images[hashSeed(seed) % images.length];
}

function hashSeed(value: string) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  }
  return hash;
}
