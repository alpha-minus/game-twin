#!/usr/bin/env bash
set -euo pipefail
FIX=app/src/test/resources/epub
mkdir -p "$FIX"

# --- EPUB 3 fixture ---
WORK="$(mktemp -d)"
WORK2="$(mktemp -d)"
trap 'rm -rf "$WORK" "$WORK2"' EXIT
cd "$WORK"

printf 'application/epub+zip' > mimetype
mkdir -p META-INF OEBPS

cat > META-INF/container.xml <<'XML'
<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
XML

cat > OEBPS/package.opf <<'OPF'
<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bid">urn:test:epub3-fixture</dc:identifier>
    <dc:title>EPUB3 Fixture</dc:title>
    <dc:language>en</dc:language>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="img1" href="img/test.png" media-type="image/png"/>
  </manifest>
  <spine>
    <itemref idref="ch1"/>
    <itemref idref="ch2"/>
  </spine>
</package>
OPF

cat > OEBPS/nav.xhtml <<'NAV'
<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <head><title>Contents</title></head>
  <body>
    <nav epub:type="toc">
      <ol>
        <li><a href="ch1.xhtml">Chapter One</a></li>
        <li><a href="ch2.xhtml">Chapter Two</a></li>
      </ol>
    </nav>
  </body>
</html>
NAV

cat > OEBPS/ch1.xhtml <<'XHTML'
<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
  <head><title>Ch 1</title></head>
  <body>
    <h1>Chapter One</h1>
    <p>Hello <img src="img/test.png" alt="test"/></p>
  </body>
</html>
XHTML

cat > OEBPS/ch2.xhtml <<'XHTML'
<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
  <head><title>Ch 2</title></head>
  <body>
    <h1>Chapter Two</h1>
    <p>World</p>
  </body>
</html>
XHTML

mkdir -p OEBPS/img
# 1x1 transparent PNG (base64-encoded to avoid raw-byte escaping issues)
base64 -d <<'B64' > OEBPS/img/test.png
iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9
awAAAABJRU5ErkJggg==
B64

# Zip with mimetype first (stored, no compression), then the rest deflated
zip -X0 minimal.epub mimetype
zip -Xr9D minimal.epub META-INF OEBPS
mv minimal.epub "$OLDPWD/$FIX/minimal-epub3.epub"
cd "$OLDPWD"

# --- EPUB 2 fixture (uses toc.ncx) ---
cd "$WORK2"
printf 'application/epub+zip' > mimetype
mkdir -p META-INF OEBPS

cat > META-INF/container.xml <<'XML'
<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
XML

cat > OEBPS/content.opf <<'OPF'
<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:identifier id="bid" opf:scheme="URI">urn:test:epub2-fixture</dc:identifier>
    <dc:title>EPUB2 Fixture</dc:title>
    <dc:language>en</dc:language>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="ch1" href="ch1.html" media-type="application/xhtml+xml"/>
    <item id="ch2" href="ch2.html" media-type="application/xhtml+xml"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="ch1"/>
    <itemref idref="ch2"/>
  </spine>
</package>
OPF

cat > OEBPS/toc.ncx <<'NCX'
<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="urn:test:epub2-fixture"/></head>
  <docTitle><text>EPUB2 Fixture</text></docTitle>
  <navMap>
    <navPoint id="p1" playOrder="1"><navLabel><text>Chapter One</text></navLabel><content src="ch1.html"/></navPoint>
    <navPoint id="p2" playOrder="2"><navLabel><text>Chapter Two</text></navLabel><content src="ch2.html"/></navPoint>
  </navMap>
</ncx>
NCX

cat > OEBPS/ch1.html <<'XHTML'
<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Ch 1</title></head><body><h1>Chapter One</h1><p>Alpha</p></body></html>
XHTML

cat > OEBPS/ch2.html <<'XHTML'
<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Ch 2</title></head><body><h1>Chapter Two</h1><p>Beta</p></body></html>
XHTML

zip -X0 minimal.epub mimetype
zip -Xr9D minimal.epub META-INF OEBPS
mv minimal.epub "$OLDPWD/$FIX/minimal-epub2.epub"
cd "$OLDPWD"

echo "Wrote $FIX/minimal-epub2.epub and $FIX/minimal-epub3.epub"
