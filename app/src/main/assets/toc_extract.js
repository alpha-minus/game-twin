// Extracts every <a> in document.body and emits a JSON array of
// { text, href, y, standalone } objects. Applies cheap per-link filters
// here so the Kotlin detector only sees same-origin, http(s),
// text-bounded links.
// `y` is the link's absolute document Y in CSS pixels
// (rect.top + window.scrollY); -1 when the rect is unavailable (hidden
// element, no layout). The Kotlin detector uses `y` to identify the
// top-of-page ToC cluster and discard scattered same-URL anchors
// elsewhere in the body.
// `standalone` is true when the link's text dominates its immediate
// parent element's text content (ratio >= 0.65), false when the link
// is a small fragment inside a longer sentence. ToC items sit in their
// own <li> (ratio ~1.0); inline body references sit inside <p> / <td>
// sentences (ratio < 0.3). The Kotlin detector uses this to drop
// inline body anchors that the Y-cluster heuristic can't separate from
// the ToC when the two sit close together vertically.
// One fallback: the first TEXT-BEARING anchor of a link-dense parent
// (combined anchor text >= 0.65 of the parent's text) is standalone
// even when variant links dilute its own ratio. Guide sites list
// reference pages as multi-link rows —
// `<a>Energy Tanks</a> (<a>By Progression</a>) (<a>By Area</a>)` —
// inside <li>s on desktop layouts and <h3><span>s on the mobile layout
// the WebView actually renders; no single anchor dominates, but the row
// is a ToC item, not a sentence. The link-density bound keeps prose
// (mostly non-link text) filtered, first-anchor-only keeps the variant
// links (duplicates of the same page) out, and "text-bearing" skips
// the text-less svg heading-anchor links some themes prepend to rows.
(function () {
  var loc = window.location;
  var scrollY = window.scrollY || window.pageYOffset || 0;
  var out = [];
  var anchors = document.body ? document.body.querySelectorAll('a[href]') : [];
  var MAX_LINKS = 2000;
  for (var i = 0; i < anchors.length && out.length < MAX_LINKS; i++) {
    var a = anchors[i];
    var text = (a.textContent || '').trim();
    if (text.length < 2 || text.length > 200) continue;
    var raw = a.getAttribute('href');
    if (!raw || raw === '' || raw === '/' || raw === '#') continue;
    var resolved;
    try { resolved = new URL(a.href, loc.href); } catch (e) { continue; }
    if (resolved.protocol !== 'http:' && resolved.protocol !== 'https:') continue;
    if (resolved.host !== loc.host) continue;
    var y = -1;
    try {
      var rect = a.getBoundingClientRect();
      if (rect && (rect.width > 0 || rect.height > 0 || rect.top !== 0 || rect.left !== 0)) {
        y = Math.round(rect.top + scrollY);
      }
    } catch (e) { y = -1; }
    var standalone = true;
    try {
      var parent = a.parentElement;
      var parentText = parent ? (parent.textContent || '').trim() : '';
      standalone = parentText.length === 0 || (text.length / parentText.length) >= 0.65;
      if (!standalone) {
        var rowAnchors = parent.querySelectorAll('a[href]');
        var linkChars = 0;
        var firstTextAnchor = null;
        for (var j = 0; j < rowAnchors.length; j++) {
          var rowText = (rowAnchors[j].textContent || '').trim();
          linkChars += rowText.length;
          if (firstTextAnchor === null && rowText.length >= 2) firstTextAnchor = rowAnchors[j];
        }
        standalone = firstTextAnchor === a && (linkChars / parentText.length) >= 0.65;
      }
    } catch (e) { standalone = true; }
    // Keep same-page anchors here. The Kotlin detector decides per link
    // whether the link's stripped URL is one of the chapter URLs the
    // tail/parent/query passes picked; only then does the anchor entry
    // survive. Dropping anchors at this layer would lose ToC sub-entries
    // for guides whose ToC indexes anchored sections of a parent page.
    out.push({ text: text, href: resolved.href, y: y, standalone: standalone });
  }
  return JSON.stringify(out);
})();
