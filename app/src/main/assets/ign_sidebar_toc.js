// IGN wiki sidebar chapter extractor (group-page downloader).
//
// IGN walkthroughs render their chapter list as `<button title="...">`
// elements (React/Next.js client-side nav) with NO href, so the generic
// `a[href]` extractor (toc_extract.js) sees no chapters. This collects the
// sidebar button LABELS (in DOM order) plus the page's `/wikis/<slug>/<page>`
// content anchors, and hands both to the Kotlin side, which joins label->href
// by exact text (ToCDetector.detectIgnSidebar). The join is self-filtering:
// tool/header buttons ("Find in guide", "Walkthrough", …) have no matching
// content anchor and drop out.
//
// Returns JSON `{ labels: [...], anchors: [{text, href}, ...] }`. Empty arrays
// when the sidebar ToC area is absent. Host gating + self-link + scheme checks
// live on the Kotlin side; this layer only scrapes the DOM.
(function () {
  var EMPTY = JSON.stringify({ labels: [], anchors: [] });
  // "That area": the sidebar table-of-contents scroll container. All chapter
  // (and tool) items live under it as `.navigation-item.sidebar-item[title]`.
  var toc = document.querySelector('.wiki-toc');
  if (!toc) return EMPTY;

  var items = toc.querySelectorAll('.navigation-item.sidebar-item[title]');
  var labels = [];
  for (var i = 0; i < items.length; i++) {
    var t = (items[i].getAttribute('title') || '').trim();
    if (t) labels.push(t);
  }
  if (labels.length === 0) return EMPTY;

  // Content anchors anywhere in the doc: text + resolved href, pre-filtered to
  // `/wikis/<slug>/<page>` shapes so we don't ship the whole page's link set.
  var anchors = [];
  var loc = window.location;
  var as = document.querySelectorAll('a[href]');
  for (var j = 0; j < as.length && anchors.length < 4000; j++) {
    var a = as[j];
    var text = (a.textContent || '').trim();
    if (text.length < 1 || text.length > 200) continue;
    var resolved;
    try { resolved = new URL(a.href, loc.href); } catch (e) { continue; }
    if (resolved.protocol !== 'http:' && resolved.protocol !== 'https:') continue;
    var segs = resolved.pathname.split('/').filter(function (s) { return s.length > 0; });
    if (segs.length < 3 || segs[0].toLowerCase() !== 'wikis') continue;
    anchors.push({ text: text, href: resolved.href });
  }
  return JSON.stringify({ labels: labels, anchors: anchors });
})();
