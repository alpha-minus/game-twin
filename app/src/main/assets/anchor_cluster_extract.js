// Single-guide ToC extractor. Sequential passes — first one that
// produces >= 3 entries wins:
//
//   1) TXT-FAQ `.toc-heading[id]` divs injected by the text converter.
//   2) Steam Community Guide `#GuideTableOfContents` listing (this is
//      the official sidebar Steam guides ship with).
//   3) HTML guides — Y-cluster `<a href="#fragment">` anchors. Filter to
//      same-page anchors (href starts with '#' OR resolves to the same
//      path+query and has a non-empty hash whose target element exists).
//      Drop non-standalone (text/parentText ratio < 0.65).
//   4) Generic heading fallback — any h1-h4 cluster that has at least
//      3 entries spaced > 100 CSS px apart. Missing ids are stamped
//      with `gt-h-N` so the scroll bridge can find them.
//
// Returns JSON array of `{ label, fragment, targetScrollY }`. Empty array
// when nothing reaches the threshold.
(function() {
  // ---- Pass 1: TXT-FAQ heading divs / converter spans ----
  // The text-to-HTML converter emits <span class="toc-heading" id="sec-N"
  // data-title="…"></span> markers that are visually invisible (font-size:0,
  // width:0, height:0) so their `textContent` is empty. Read the label from
  // `data-title` first, fall back to textContent for any other heading
  // element that uses the same class but carries its text inline.
  var tocHeadings = document.querySelectorAll('.toc-heading[id]');
  if (tocHeadings.length >= 3) {
    var passOne = [];
    for (var i = 0; i < tocHeadings.length; i++) {
      var h = tocHeadings[i];
      var rect = h.getBoundingClientRect();
      var y = Math.round(rect.top + (window.pageYOffset || 0));
      var label = (h.getAttribute('data-title') || h.textContent || '').trim();
      if (!label) continue;
      passOne.push({ label: label, fragment: h.id, targetScrollY: y });
    }
    if (passOne.length >= 3) return JSON.stringify(passOne);
  }
  // ---- Pass 2: Steam Community Guide ----
  // Steam ships an inline ToC at `#GuideTableOfContents`. Each entry is
  // a `.rightbox_list_option` with `id="guideSectionSelection_<N>"`; the
  // body section it points at is `id="<N>"`. The "0" entry is the
  // Overview shown at the top of the doc (no separate target).
  var steamToc = document.getElementById('GuideTableOfContents');
  if (steamToc) {
    var steamItems = steamToc.querySelectorAll('[id^="guideSectionSelection_"]');
    var steamOut = [];
    for (var si = 0; si < steamItems.length; si++) {
      var item = steamItems[si];
      var rawId = (item.id || '').replace('guideSectionSelection_', '');
      if (!rawId || rawId === '-1') continue;
      var labelEl = item.querySelector('.guideSubSectionSelectionLink a, .guideSubSectionSelectionLink span') || item.querySelector('a, span');
      var label = labelEl ? (labelEl.textContent || '').trim() : '';
      if (!label) continue;
      var targetEl = null;
      if (rawId === '0') {
        // Overview row scrolls to top.
        steamOut.push({ label: label, fragment: rawId, targetScrollY: 0 });
        continue;
      }
      targetEl = document.getElementById(rawId);
      if (!targetEl) continue;
      var sRect = targetEl.getBoundingClientRect();
      var sY = Math.round(sRect.top + (window.pageYOffset || 0));
      steamOut.push({ label: label, fragment: rawId, targetScrollY: sY });
    }
    if (steamOut.length >= 3) return JSON.stringify(steamOut);
  }
  // ---- Pass 3: HTML anchor-link Y-cluster ----
  var raw = [];
  var anchors = document.body ? document.body.querySelectorAll('a[href]') : [];
  for (var j = 0; j < anchors.length; j++) {
    var a = anchors[j];
    var href = a.getAttribute('href') || '';
    if (!href) continue;
    // Extract fragment from any href shape. We DON'T require the href's
    // path+query to match window.location — saved guides loaded via
    // loadDataWithBaseURL have window.location at "/" but their <a> tags
    // carry the original fully-qualified self-references. The
    // document.getElementById check below is the actual "same-page"
    // guarantee.
    var fragment = '';
    if (href.charAt(0) === '#') {
      fragment = href.substring(1);
    } else {
      var hashIdx = href.indexOf('#');
      if (hashIdx >= 0) fragment = href.substring(hashIdx + 1);
    }
    if (!fragment) continue;
    // URL-decode the fragment: hrefs URL-encode spaces and other chars
    // (`#Bomb%20Climbing`) while the target's `id` attribute carries the
    // literal value (`id="Bomb Climbing"`). Without decoding, getElementById
    // misses any multi-word chapter on guides like Neoseeker FAQs.
    var decodedFragment = fragment;
    try { decodedFragment = decodeURIComponent(fragment); } catch (e) { /* keep raw */ }
    // GitHub wiki prefixes heading ids with 'user-content-' while its ToC
    // links point at the bare slug (its client JS bridges the two online but
    // never runs offline) — try the prefixed id before discarding the entry.
    var target = document.getElementById(decodedFragment) ||
                 document.getElementById('user-content-' + decodedFragment.toLowerCase()) ||
                 document.querySelector('[name="' + decodedFragment.replace(/"/g, '\\"') + '"]');
    if (!target) continue;
    var text = (a.textContent || '').trim();
    if (text.length < 2 || text.length > 200) continue;
    var rect = a.getBoundingClientRect();
    var y = Math.round(rect.top + (window.pageYOffset || 0));
    var parent = a.parentElement;
    var parentText = parent ? (parent.textContent || '').trim() : '';
    var standalone = parentText.length === 0 || (text.length / parentText.length) >= 0.65;
    if (!standalone) continue;
    var targetRect = target.getBoundingClientRect();
    var targetY = Math.round(targetRect.top + (window.pageYOffset || 0));
    raw.push({ text: text, fragment: decodedFragment, y: y, targetY: targetY });
  }
  if (raw.length < 3) return JSON.stringify([]);
  // Sort by anchor Y, partition where consecutive gap > 200px, take
  // largest cluster.
  raw.sort(function(a, b) { return a.y - b.y; });
  var clusters = [[raw[0]]];
  for (var k = 1; k < raw.length; k++) {
    if (raw[k].y - raw[k - 1].y > 200) clusters.push([raw[k]]);
    else clusters[clusters.length - 1].push(raw[k]);
  }
  var best = clusters[0];
  for (var c = 1; c < clusters.length; c++) {
    if (clusters[c].length > best.length) best = clusters[c];
  }
  if (best.length >= 3) {
    // Dedup by fragment, first wins.
    var seen = {};
    var final = [];
    for (var m = 0; m < best.length; m++) {
      var e = best[m];
      if (seen[e.fragment]) continue;
      seen[e.fragment] = true;
      final.push({ label: e.text, fragment: e.fragment, targetScrollY: e.targetY });
    }
    return JSON.stringify(final);
  }
  // ---- Pass 4: Generic heading fallback ----
  // Last resort: scan h1-h4 for a vertical cluster. Headings without an
  // id get stamped with `gt-h-N` so the scroll bridge can resolve them
  // via getElementById. Filters out invisible / off-canvas elements and
  // text > 200 chars (paragraph-like).
  var hs = document.querySelectorAll('h1, h2, h3, h4');
  var headingRaw = [];
  for (var hi = 0; hi < hs.length; hi++) {
    var hEl = hs[hi];
    var hText = (hEl.textContent || '').trim();
    if (hText.length < 2 || hText.length > 200) continue;
    var hRect = hEl.getBoundingClientRect();
    if (hRect.width < 10 || hRect.height < 10) continue;
    var hY = Math.round(hRect.top + (window.pageYOffset || 0));
    headingRaw.push({ el: hEl, text: hText, y: hY });
  }
  if (headingRaw.length < 3) return JSON.stringify([]);
  // Cluster by Y with a tighter gap than the anchor pass (headings are
  // typically denser through a doc) — 1500px so a long body section
  // doesn't break the chapter group.
  headingRaw.sort(function(a, b) { return a.y - b.y; });
  var hClusters = [[headingRaw[0]]];
  for (var hk = 1; hk < headingRaw.length; hk++) {
    if (headingRaw[hk].y - headingRaw[hk - 1].y > 1500) hClusters.push([headingRaw[hk]]);
    else hClusters[hClusters.length - 1].push(headingRaw[hk]);
  }
  var bestH = hClusters[0];
  for (var hc = 1; hc < hClusters.length; hc++) {
    if (hClusters[hc].length > bestH.length) bestH = hClusters[hc];
  }
  if (bestH.length < 3) return JSON.stringify([]);
  var headingOut = [];
  for (var hm = 0; hm < bestH.length; hm++) {
    var entry = bestH[hm];
    var id = entry.el.id;
    if (!id) {
      id = 'gt-h-' + hm;
      try { entry.el.setAttribute('id', id); } catch (e) { continue; }
    }
    headingOut.push({ label: entry.text, fragment: id, targetScrollY: entry.y });
  }
  return JSON.stringify(headingOut);
})();
