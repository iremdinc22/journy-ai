import type { LanguageCode } from '../i18n/LanguageContext';

export function localizeDynamicText(value: string | null | undefined, language: LanguageCode) {
  if (!value || language !== 'tr') {
    return value ?? '';
  }

  let text = value.trim();
  text = translatePlanningSentence(text);
  text = translateInsightSentence(text);
  text = translateCommonPhrases(text);
  return cleanupTurkishDynamicText(text);
}

export function localizeDynamicList(values: string[] | null | undefined, language: LanguageCode) {
  return (values ?? []).map((item) => localizeDynamicText(item, language));
}

function translatePlanningSentence(text: string) {
  const match = text.match(/^(.+?) route, (.+?) and (.+?)(?: in (.+?))?(?: starting from (.+?))?\.$/i);
  if (!match) {
    return text;
  }

  const [, focus, rhythm, spend, city, area] = match;
  const parts = [
    `${translateRouteFocus(focus)} rota`,
    translateRhythm(rhythm),
    translateSpend(spend),
  ];

  if (city) parts.push(city);
  if (area) parts.push(`başlangıç: ${area}`);
  return parts.filter(Boolean).join(' · ') + '.';
}

function translateInsightSentence(text: string): string {
  if (/^Tapas Street Window$/i.test(text)) {
    return 'Tapas Sokağı';
  }

  if (/^Easy dinner zone with short transfers after the final stop\.?$/i.test(text)) {
    return 'Son duraktan sonra kısa geçişlerle ulaşılabilen rahat tapas ve akşam yemeği bölgesi.';
  }

  if (/^Seasonal plates in a calm neighborhood room\.?$/i.test(text)) {
    return 'Sakin bir semt atmosferinde mevsimlik tabaklar.';
  }

  if (/^Morning pastries and simple coffee near the route\.?$/i.test(text)) {
    return 'Rotaya yakın sabah hamur işleri ve sade kahve molası.';
  }

  if (/^A compact culture stop that keeps the day balanced\.?$/i.test(text)) {
    return 'Günü dengede tutan kompakt bir kültür durağı.';
  }

  if (/^Independent galleries close to cafe streets\.?$/i.test(text)) {
    return 'Kafe sokaklarına yakın bağımsız galeriler.';
  }

  if (/^A soft-paced cafe for planning the next stop\.?$/i.test(text)) {
    return 'Sonraki durağı planlamak için sakin tempolu kafe molası.';
  }

  if (/^Good espresso without a long detour\.?$/i.test(text)) {
    return 'Uzun sapma yapmadan iyi espresso molası.';
  }

  if (/^Low-cost local browsing and quick bites\.?$/i.test(text)) {
    return 'Düşük maliyetli yerel keşif ve hızlı atıştırmalıklar.';
  }

  if (/^A quiet canal-side walking window with local streets and small shops\.?$/i.test(text)) {
    return 'Yerel sokaklar ve küçük dükkanlarla sakin kanal kenarı yürüyüş aralığı.';
  }

  if (/^A focused culture stop that pairs well with a river walk\.?$/i.test(text)) {
    return 'Nehir yürüyüşüyle iyi eşleşen odaklı kültür durağı.';
  }

  if (/^A calm cafe break between gallery streets and the Seine\.?$/i.test(text)) {
    return 'Galeri sokakları ve Seine arasında sakin kafe molası.';
  }

  if (/^Morning pastry stop that keeps the route light and local\.?$/i.test(text)) {
    return 'Rotayı hafif ve yerel tutan sabah tatlı durağı.';
  }

  if (/^Compact culture before lunch, close to food streets\.?$/i.test(text)) {
    return 'Öğle yemeği öncesi, yemek sokaklarına yakın kompakt kültür durağı.';
  }

  if (/^Local plates in a neighborhood built for food-first days\.?$/i.test(text)) {
    return 'Yemek odaklı günler için ideal semtte yerel tabaklar.';
  }

  if (/^A short espresso break between historic anchors\.?$/i.test(text)) {
    return 'Tarihi ana duraklar arasında kısa espresso molası.';
  }

  if (/^Low-effort streets for dinner and a flexible evening\.?$/i.test(text)) {
    return 'Akşam yemeği ve esnek akşam için yormayan sokaklar.';
  }

  if (/^Independent galleries and design shops grouped into one area\.?$/i.test(text)) {
    return 'Tek bölgede toplanmış bağımsız galeriler ve tasarım mağazaları.';
  }

  if (/^A softer cafe stop away from the busiest tourist streets\.?$/i.test(text)) {
    return 'En kalabalık turistik sokaklardan uzakta daha sakin kafe durağı.';
  }

  if (/^Free wandering through small lanes, plazas and local corners\.?$/i.test(text)) {
    return 'Küçük sokaklar, meydanlar ve yerel köşeler arasında ücretsiz keşif.';
  }

  if (/^A flexible riverside culture route with galleries and easy food options nearby\.?$/i.test(text)) {
    return 'Yakında kolay yemek seçenekleri olan, galeri odaklı esnek nehir kenarı kültür rotası.';
  }

  if (/^A high-choice food stop that works well for mixed budgets and short detours\.?$/i.test(text)) {
    return 'Farklı bütçelere uyan, kısa sapmayla ulaşılabilen bol seçenekli yemek durağı.';
  }

  if (/^A compact coffee break between central culture and evening areas\.?$/i.test(text)) {
    return 'Merkezi kültür durakları ve akşam bölgeleri arasında kompakt kahve molası.';
  }

  if (/^A free open-air reset that keeps a full city day from becoming too dense\.?$/i.test(text)) {
    return 'Dolu bir şehir gününü fazla sıkıştırmadan rahatlatan ücretsiz açık hava molası.';
  }

  if (/^A reliable local dinner zone that keeps the evening practical and low-stress\.?$/i.test(text)) {
    return 'Akşamı pratik ve sakin tutan güvenilir yerel yemek bölgesi.';
  }

  if (/^A compact dinner zone with local restaurants and low route friction at night\.?$/i.test(text)) {
    return 'Gece rotasını yormayan, yerel restoranların toplandığı kompakt yemek bölgesi.';
  }

  if (/^Easy dinner zone/i.test(text)) {
    return text.replace(/^Easy dinner zone/i, 'Rahat akşam yemeği bölgesi');
  }

  const compactStop = text.match(/^A compact (.+?) stop that (?:pairs well with|keeps|fits) (.+?)\.?$/i);
  if (compactStop) {
    return `${translateLooseCategory(compactStop[1])} için kompakt durak; ${translateLooseList(compactStop[2])} ile uyumlu.`;
  }

  const freeWindow = text.match(/^A free (.+?) window that (.+?)\.?$/i);
  if (freeWindow) {
    return `Ücretsiz ${translateLooseCategory(freeWindow[1])} aralığı; ${translateLooseList(freeWindow[2])}.`;
  }

  const calmBreak = text.match(/^A calm (.+?) break (?:between|near) (.+?)\.?$/i);
  if (calmBreak) {
    return `${translateLooseCategory(calmBreak[1])} için sakin mola; ${translateLooseList(calmBreak[2])} yakınında.`;
  }

  const flexibleStop = text.match(/^A flexible (.+?) stop with (.+?)\.?$/i);
  if (flexibleStop) {
    return `Esnek ${translateLooseCategory(flexibleStop[1])} durağı; ${translateLooseList(flexibleStop[2])}.`;
  }

  const goodFits = text.match(/^(.+?) has (\d+) good fits? for this trip\.?$/i);
  if (goodFits) {
    return `${goodFits[1]} bu seyahat için ${goodFits[2]} uygun seçenek sunuyor.`;
  }

  const startSave = text.match(/^Starting from (.+?) can (?:save|reduce).*?~?([\d.,]+) km.*?(?:first leg|first-leg walk).*$/i);
  if (startSave) {
    return `${startSave[1]} başlangıcı ilk yürüyüşte yaklaşık ${startSave[2]} km kazandırabilir.`;
  }

  const freeScenic = text.match(/^A free scenic route for (.+?) without adding reservations\.?$/i);
  if (freeScenic) {
    return `Rezervasyon gerektirmeyen, ${freeScenic[1]} için ücretsiz manzaralı rota.`;
  }

  const localFood = text.match(/^A local food candidate for your (.+?) route\.?$/i);
  if (localFood) {
    return `${localFood[1]} rotan için yerel yemek adayı.`;
  }

  const coffeeBreak = text.match(/^A coffee break that can fit into your (.+?) day\.?$/i);
  if (coffeeBreak) {
    return `${coffeeBreak[1]} gününe uyabilecek kahve molası.`;
  }

  const cultureAnchor = text.match(/^A culture anchor candidate for a stronger (.+?) itinerary\.?$/i);
  if (cultureAnchor) {
    return `Daha güçlü bir ${cultureAnchor[1]} planı için kültür durağı adayı.`;
  }

  const lowCost = text.match(/^A low-cost (.+?) pick for flexible pacing\.?$/i);
  if (lowCost) {
    return `Esnek tempo için düşük maliyetli ${lowCost[1]} önerisi.`;
  }

  const starter = text.match(/^A starter recommendation shaped around your current (.+?) trip\.?$/i);
  if (starter) {
    return `Mevcut ${starter[1]} seyahatine göre hazırlanmış başlangıç önerisi.`;
  }

  const selected = text.match(/^You selected (.+?)\. This pick keeps the route compact and fits your (.+?) budget\.?$/i);
  if (selected) {
    return `${translateLooseList(selected[1])} seçtin. Bu öneri rotayı kompakt tutar ve ${translateLooseCategory(selected[2])} bütçene uyar.`;
  }

  const profileStrategy = text.match(/^Journy is shaping (.+?) around (\d+) days?, (.+?) spend and (.+?) daily rhythm\.?$/i);
  if (profileStrategy) {
    return `Journy ${profileStrategy[1]} planını ${profileStrategy[2]} gün, ${translateLooseCategory(profileStrategy[3])} harcama ve ${translateLooseCategory(profileStrategy[4])} günlük ritimle şekillendiriyor.`;
  }

  const savedCount = text.match(/^(\d+) saved (place|places)$/i);
  if (savedCount) {
    return `${savedCount[1]} kayıtlı yer`;
  }

  const behaviorSignals = text.match(/^(\d+) behavior (signal|signals)$/i);
  if (behaviorSignals) {
    return `${behaviorSignals[1]} davranış sinyali`;
  }

  if (/^From TripSetup choices$/i.test(text)) {
    return 'TripSetup seçimlerinden';
  }

  if (/^Learned from your route$/i.test(text)) {
    return 'Rotandan öğrenildi';
  }

  if (/^Nothing planned yet$/i.test(text)) {
    return 'Henüz plan oluşturulmadı';
  }

  if (/^Create an itinerary first so Journy can recommend what to do now\.?$/i.test(text)) {
    return 'Journy şimdi ne yapacağını önerebilmek için önce bir gezi planına ihtiyaç duyar.';
  }

  if (/^You are here$/i.test(text)) {
    return 'Buradasın';
  }

  if (/^Finish this stop when you are ready, or skip it if you want to keep the day moving\.?$/i.test(text)) {
    return 'Hazır olduğunda bu durağı tamamla; günü akışta tutmak istersen atlayabilirsin.';
  }

  if (/^You are done for today$/i.test(text)) {
    return 'Bugünlük plan tamam';
  }

  if (/^All planned stops are completed\. Keep the rest of the day flexible\.?$/i.test(text)) {
    return 'Planlanan tüm duraklar tamamlandı. Günün kalanını esnek bırakabilirsin.';
  }

  if (/^Adjust your next move$/i.test(text)) {
    return 'Sıradaki hamleni ayarla';
  }

  if (/^What should I do now\??$/i.test(text)) {
    return 'Şimdi ne yapmalıyım?';
  }

  const behindMessage = text.match(/^You are running about (\d+) min behind schedule\. Journy recommends keeping the next stop simple\.?$/i);
  if (behindMessage) {
    return `Programın yaklaşık ${behindMessage[1]} dk gerisindesin. Journy sıradaki durağı sade tutmanı öneriyor.`;
  }

  const rightNowWindow = text.match(/^You have about (\d+) min before the next planned window\.?$/i);
  if (rightNowWindow) {
    return `Sıradaki plan aralığına yaklaşık ${rightNowWindow[1]} dk var.`;
  }

  const currentStopMeta = text.match(/^(.+?) · current stop · (.+)$/i);
  if (currentStopMeta) {
    return `${currentStopMeta[1]} · mevcut durak · ${translateInsightSentence(currentStopMeta[2])}`;
  }

  const normalStopMeta = text.match(/^(.+?) · (.+?) · (.+)$/i);
  if (normalStopMeta && /min away/i.test(normalStopMeta[3])) {
    return `${normalStopMeta[1]} · ${translateLooseCategory(normalStopMeta[2])} · ${translateInsightSentence(normalStopMeta[3])}`;
  }

  const minAway = text.match(/^(\d+) min away$/i);
  if (minAway) {
    return `${minAway[1]} dk uzaklıkta`;
  }

  const rainRisk = text.match(/^Rain risk (.+)$/i);
  if (rainRisk) {
    return `Yağmur riski ${rainRisk[1]}`;
  }

  if (/^Weather clear enough$/i.test(text)) {
    return 'Hava uygun görünüyor';
  }

  const stopsDone = text.match(/^(\d+)\/(\d+) stops done$/i);
  if (stopsDone) {
    return `${stopsDone[1]}/${stopsDone[2]} durak tamamlandı`;
  }

  if (/^On schedule$/i.test(text)) {
    return 'Program zamanında';
  }

  const minBehind = text.match(/^(\d+) min behind$/i);
  if (minBehind) {
    return `${minBehind[1]} dk geride`;
  }

  const paceChip = text.match(/^(.+?) pace$/i);
  if (paceChip) {
    return `${translateLooseCategory(paceChip[1])} tempo`;
  }

  if (/^Marked as your current stop$/i.test(text)) {
    return 'Mevcut durağın olarak işaretlendi';
  }

  if (/^Part of the main route$/i.test(text)) {
    return 'Ana rotanın parçası';
  }

  if (/^Optional, so it can be skipped if needed$/i.test(text)) {
    return 'Opsiyonel; gerekirse atlanabilir';
  }

  if (/^Day is still on track$/i.test(text)) {
    return 'Gün hala yolunda';
  }

  const nextUnfinished = text.match(/^Next unfinished stop in Day (\d+)$/i);
  if (nextUnfinished) {
    return `${nextUnfinished[1]}. günün sıradaki tamamlanmamış durağı`;
  }

  const scheduleBehind = text.match(/^Schedule is behind by about (\d+) min$/i);
  if (scheduleBehind) {
    return `Program yaklaşık ${scheduleBehind[1]} dk geride`;
  }

  if (/^Fits the current route rhythm$/i.test(text)) {
    return 'Mevcut rota ritmine uyuyor';
  }

  const allStopsDone = text.match(/^All stops for Day (\d+) are completed$/i);
  if (allStopsDone) {
    return `${allStopsDone[1]}. günün tüm durakları tamamlandı`;
  }

  if (/^Journy keeps the evening open$/i.test(text)) {
    return 'Journy akşamı esnek bırakıyor';
  }

  const rainTitle = text.match(/^Rain risk around Day (\d+) at (.+)$/i);
  if (rainTitle) {
    return `${rainTitle[1]}. gün ${rainTitle[2]} civarında yağmur riski var.`;
  }

  const weatherSensitive = text.match(/^Weather-sensitive route around Day (\d+)$/i);
  if (weatherSensitive) {
    return `${weatherSensitive[1]}. gün hava durumuna duyarlı rota.`;
  }

  const rainMessage = text.match(/^Journy can protect the wettest window by moving (.+?) earlier and using (.+?) as the safer afternoon anchor\.?$/i);
  if (rainMessage) {
    return `Journy en yağışlı aralığı korumak için ${rainMessage[1]} durağını erkene alıp ${rainMessage[2]} durağını daha güvenli öğleden sonra ana durağı yapabilir.`;
  }

  return text;
}

function translateCommonPhrases(text: string) {
  const replacements: Array<[RegExp, string]> = [
    [/\bCulture-led route\b/gi, 'Kültür odaklı rota'],
    [/\bFood-led route\b/gi, 'Yemek odaklı rota'],
    [/\bArea-first route\b/gi, 'Bölge odaklı rota'],
    [/\bBalanced city route\b/gi, 'Dengeli şehir rotası'],
    [/\bFree activities\b/gi, 'Ücretsiz aktiviteler'],
    [/\bLocal food\b/gi, 'Yerel yemek'],
    [/\bCoffee breaks\b/gi, 'Kahve molaları'],
    [/\bLow-cost picks\b/gi, 'Ekonomik öneriler'],
    [/\bEasy walking\b/gi, 'Rahat yürüyüş'],
    [/\bRoute rhythm\b/gi, 'Rota ritmi'],
    [/\bcoffee-aware\b/gi, 'kahve odaklı'],
    [/\bwalkable\b/gi, 'yürünebilir'],
    [/\bbalanced plan\b/gi, 'dengeli plan'],
    [/\bplan\b/gi, 'plan'],
    [/\bEasy flow\b/gi, 'Rahat akış'],
    [/\bLow-cost\b/gi, 'Ekonomik'],
    [/\bFood-led\b/gi, 'Yemek odaklı'],
    [/\bCulture-led\b/gi, 'Kültür odaklı'],
    [/\bArea-first\b/gi, 'Bölge odaklı'],
    [/\bBalanced\b/gi, 'Dengeli'],
    [/\bRelaxed\b/gi, 'Rahat'],
    [/\bFull\b/gi, 'Dolu'],
    [/\bOld Town\b/gi, 'Eski şehir'],
    [/\bWaterfront\b/gi, 'Sahil'],
    [/\bNeighborhood\b/gi, 'Semt'],
    [/\bHeritage Quarter\b/gi, 'Tarihi bölge'],
    [/\bMuseum\b/gi, 'Müze'],
    [/\bGallery\b/gi, 'Galeri'],
    [/\bCulture\b/gi, 'Kültür'],
    [/\bCoffee\b/gi, 'Kahve'],
    [/\bEspresso\b/gi, 'Espresso'],
    [/\bBakery\b/gi, 'Fırın'],
    [/\bFood Streets\b/gi, 'Yemek sokakları'],
    [/\bTapas Street Window\b/gi, 'Tapas Sokağı'],
    [/\bTapas Street\b/gi, 'Tapas Sokağı'],
    [/\bDinner Window\b/gi, 'Akşam yemeği aralığı'],
    [/\bDinner Lane\b/gi, 'Yemek sokağı'],
    [/\bDinner\b/gi, 'Akşam yemeği'],
    [/\bStreet\b/gi, 'Sokağı'],
    [/\blocal food stop\b/gi, 'yerel yemek durağı'],
    [/\bcoffee pause\b/gi, 'kahve molası'],
    [/\bculture window\b/gi, 'kültür aralığı'],
    [/\bfree city moment\b/gi, 'ücretsiz şehir molası'],
    [/\bstarter pick\b/gi, 'başlangıç önerisi'],
    [/\bLocal Corners\b/gi, 'Yerel köşeler'],
    [/\bGarden Route\b/gi, 'Bahçe rotası'],
    [/\bCity Core\b/gi, 'Şehir merkezi'],
    [/\bLoop\b/gi, 'turu'],
    [/\bMorning\b/gi, 'sabahı'],
    [/\bAfternoon\b/gi, 'öğleden sonra'],
    [/\bEvening\b/gi, 'akşam'],
    [/\bLate morning\b/gi, 'geç sabah'],
    [/\bWalk\b/gi, 'yürüyüşü'],
    [/\bWindow\b/gi, 'aralığı'],
    [/\bPause\b/gi, 'molası'],
    [/\bBreak\b/gi, 'molası'],
    [/\bRoute\b/gi, 'rotası'],
    [/\bStop\b/gi, 'durağı'],
    [/\bStops\b/gi, 'durak'],
    [/\bWalking\b/gi, 'yürüyüş'],
    [/\bwalk\b/g, 'yürüyüş'],
    [/\bWalkable route\b/gi, 'Yürünebilir rota'],
    [/\bFlexible budget\b/gi, 'Esnek bütçe'],
    [/\bMatches culture\b/gi, 'Kültür tercihine uyuyor'],
    [/\bMatches coffee\b/gi, 'Kahve tercihine uyuyor'],
    [/\bMatches food\b/gi, 'Yemek tercihine uyuyor'],
    [/\bLow-cost fit\b/gi, 'Bütçeye uygun'],
    [/\bTrip fit\b/gi, 'Seyahate uygun'],
    [/\bdetour\b/gi, 'sapma'],
    [/\bMove\b/gi, 'Taşı'],
    [/\bout of the rain window\b/gi, 'yağmur aralığının dışına'],
    [/\bKeep culture, cafe or food stops for\b/gi, 'Kültür, kafe veya yemek duraklarını şu aralıkta koru:'],
    [/\bPreserve the day rhythm before applying changes\b/gi, 'Değişiklikleri uygulamadan önce gün ritmini koru'],
    [/\bis weather-sensitive\b/gi, 'hava durumuna duyarlı'],
    [/\bIndoor-friendly stops reduce route risk without rebuilding the whole trip\b/gi, 'Kapalı mekana uygun duraklar tüm seyahati yeniden kurmadan rota riskini azaltır'],
    [/\bforecast shows\b/gi, 'tahmini'],
    [/\bprecipitation risk\b/gi, 'yağış riski gösteriyor'],
    [/\bForecast provider was unavailable, so Journy used the route weather-risk fallback\b/gi, 'Tahmin sağlayıcıya ulaşılamadı; Journy rota hava riski yedeğini kullandı'],
    [/\bMuseums\b/gi, 'Müzeler'],
    [/\bNightlife\b/gi, 'Gece hayatı'],
    [/\bFood\b/gi, 'Yemek'],
    [/\bpace\b/gi, 'tempo'],
    [/\bbudget\b/gi, 'bütçe'],
    [/\bFlexible route window\b/gi, 'Esnek rota aralığı'],
    [/\bcity center\b/gi, 'şehir merkezi'],
    [/\bwalkable\b/gi, 'yürünebilir'],
    [/\blocal\b/gi, 'yerel'],
    [/\byour route cluster\b/gi, 'mevcut rota alanı'],
    [/\bCurrent trip\b/gi, 'Mevcut seyahat'],
    [/\bPreview first\b/gi, 'Önce önizleme'],
    [/\bBackend needed to apply this preview\.?/gi, 'Bu önizlemeyi uygulamak için backend bağlantısı gerekir.'],
    [/\bAsk for lighter, cheaper, food-focused or rain-ready\.?/gi, 'Daha hafif, ekonomik, yemek odaklı veya yağmura hazır düzenleme iste.'],
    [/\bPlan a trip\b/gi, 'Seyahat planla'],
    [/\bOpen route\b/gi, 'Rotayı aç'],
    [/\bReview plan\b/gi, 'Planı gözden geçir'],
    [/\bGo here\b/gi, 'Buraya git'],
    [/\bcurrent stop\b/gi, 'mevcut durak'],
    [/\bBetter break\b/gi, 'Daha iyi mola'],
    [/\bIndoor-ready\b/gi, 'Kapalı mekana uygun'],
    [/\bLower cost\b/gi, 'Daha ekonomik'],
    [/\bRoute fit\b/gi, 'Rota uyumu'],
    [/\bDone\b/gi, 'Tamamlandı'],
    [/\bLighter\b/gi, 'Daha hafif'],
  ];

  return replacements.reduce((current, [pattern, replacement]) => current.replace(pattern, replacement), text);
}

function translateLooseList(value: string) {
  return value
    .replace(/\band\b/gi, 've')
    .split(/,\s*/)
    .map(translateLooseCategory)
    .join(', ');
}

function translateLooseCategory(value: string) {
  const lower = value.toLowerCase();
  if (lower.includes('museum') || lower.includes('culture')) return 'kültür';
  if (lower.includes('nightlife')) return 'gece hayatı';
  if (lower.includes('coffee')) return 'kahve';
  if (lower.includes('local food') || lower.includes('food')) return 'yemek';
  if (lower.includes('walk')) return 'yürüyüş';
  if (lower.includes('lean') || lower.includes('low')) return 'ekonomik';
  if (lower.includes('comfort')) return 'konfor';
  if (lower.includes('balanced')) return 'dengeli';
  return value;
}

function translateRouteFocus(value: string) {
  const lower = value.toLowerCase();
  if (lower.includes('culture')) return 'Kültür odaklı';
  if (lower.includes('food')) return 'Yemek odaklı';
  if (lower.includes('walk')) return 'Yürünebilir';
  if (lower.includes('area')) return 'Bölge odaklı';
  return 'Dengeli';
}

function translateRhythm(value: string) {
  const lower = value.toLowerCase();
  if (lower.includes('slow')) return 'sakin günler';
  if (lower.includes('full')) return 'dolu günler';
  return 'dengeli günler';
}

function translateSpend(value: string) {
  const lower = value.toLowerCase();
  if (lower.includes('low')) return 'ekonomik duraklar';
  if (lower.includes('comfort')) return 'konforlu duraklar';
  return 'yerel molalar';
}

function cleanupTurkishDynamicText(text: string) {
  return text
    .replace(/\s+·\s+/g, ' · ')
    .replace(/\s+-\s+/g, ' - ')
    .replace(/\bkm\/day\b/gi, 'km/gün')
    .replace(/\bmin saved\b/gi, 'dk kazanım')
    .replace(/\bmin\b/gi, 'dk')
    .replace(/\bDay (\d+)\b/gi, '$1. gün')
    .replace(/\s{2,}/g, ' ')
    .trim();
}
