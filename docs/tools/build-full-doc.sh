#!/usr/bin/env bash
# Gera docs/Documentation.md: toda a documentação (docs/**/*.md) num único documento, por ordem
# C4+1, com índice, âncoras únicas, links reescritos e apêndices (coleções Postman, lista de ficheiros).
# O conteúdo dos ficheiros é incluído tal como está; só os níveis dos títulos e os caminhos dos links mudam.
# Avisos (ficheiros sem lugar, diagramas sem .svg, links partidos) só no terminal (stderr).
# Uso (de qualquer pasta):  bash docs/tools/build-full-doc.sh
# Mesma saída que docs/tools/build-full-doc.ps1. Requisitos: bash, find, sort, grep, awk.
set -euo pipefail
export LC_ALL=C

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
OUT="docs/Documentation.md"
SERVICES="aircraftmanagement airports flightroutes maintenance"

ALL="$(find docs -type f -name '*.md' ! -path 'docs/_template/*' ! -path 'docs/tools/*' ! -path "$OUT" | sort)"
PLACED=""
CUR=""

in_list() { grep -qxF -- "$2" <<< "$1"; }   # (sem pipe: com pipefail, grep -q podia dar SIGPIPE)
# Acrescenta um ficheiro ao capítulo atual (se existir e ainda não tiver sido colocado)
add_exact() {
  if in_list "$ALL" "$1" && ! in_list "$PLACED" "$1"; then
    PLACED="$PLACED$1"$'\n'
    CUR="$CUR$1"$'\n'
  fi
}
# Acrescenta todos os .md por colocar com este prefixo, por ordem
add_under() {
  local f IFS=$'\n'
  for f in $ALL; do
    case "$f" in "$1"*) add_exact "$f" ;; esac
  done
}

CUR=""; add_exact docs/README.md; INTRO="$CUR"
CUR=""; add_under docs/1-context/; CONTEXT="$CUR"
CUR=""; for f in Containers Ports Integration-Contracts; do add_exact "docs/2-containers/$f.md"; done
add_under docs/2-containers/; CONTAINERS="$CUR"
CUR=""; add_under docs/3-components/common/; COMMON_L3="$CUR"
CUR=""; add_under docs/4-code/common/; COMMON_L4="$CUR"
CUR=""; add_under docs/5-scenarios/common/; COMMON_L5="$CUR"
SVC_FILES=()
for svc in $SERVICES; do
  CUR=""
  for level in 3-components 4-code 5-scenarios; do add_under "docs/$level/$svc/"; done
  SVC_FILES+=("$CUR")
  [ -n "$CUR" ] || echo "WARNING: no documentation found for service '$svc'; its chapter was left out" >&2
done
CUR=""; add_under docs/; EXTRA="$CUR"
IFS=$'\n'
for f in $EXTRA; do
  echo "WARNING: $f has no known place; included in Part I, chapter 'Additional documents'" >&2
done
unset IFS

PLAN=""
plan() { PLAN="$PLAN$(printf '%s\t%s' "$1" "$2")"$'\n'; }
plan_files() {
  local f IFS=$'\n'
  for f in $1; do plan FILE "$f"; done
}
chapter() { # tipo, título, ficheiros
  [ -n "$3" ] || return 0
  plan "$1" "$2"
  plan_files "$3"
}

plan H1B "Part I — System"
chapter H2 "Introduction and conventions" "$INTRO"
chapter H2 "C4 Level 1 — System context" "$CONTEXT"
chapter H2 "C4 Level 2 — Containers" "$CONTAINERS"
chapter H2 "Additional documents" "$EXTRA"
if [ -n "$COMMON_L3$COMMON_L4$COMMON_L5" ]; then
  plan H1B "Part II — Shared infrastructure (\`common\`)"
  chapter H2 "C4 Level 3 — Components" "$COMMON_L3"
  chapter H2 "C4 Level 4 — Code" "$COMMON_L4"
  chapter H2 "Scenarios (+1)" "$COMMON_L5"
fi
plan H1B "Part III — Services"
i=0
for svc in $SERVICES; do
  files="${SVC_FILES[$i]}"; i=$((i + 1))
  [ -n "$files" ] || continue
  case "$svc" in
    aircraftmanagement) title="Aircraft Management (WP1)"; owner="Tiago Moreira (1240883) — WP1 Aircraft Management" ;;
    airports)           title="Airports (WP2)";            owner="Afonso Ribeiro (1240857) — WP2 Airports" ;;
    flightroutes)       title="Flight Routes (WP3)";       owner="Tiago Caetano (1241945) — WP3 Flight Routes" ;;
    maintenance)        title="Maintenance (WP4)";         owner="Diogo Fernandes (1240632) — WP4 Maintenance" ;;
  esac
  plan H2B "$title"
  plan OWNER "$owner"
  plan_files "$files"
done
POSTMAN="$(find . \( -name .git -o -name node_modules -o -name target \) -prune -o -type f -name '*.postman_collection.json' -print | sed 's#^\./##' | sort)"
if [ -n "$POSTMAN" ]; then
  plan H1B "Appendix A — Postman collections"
  IFS=$'\n'; for f in $POSTMAN; do plan PM "$f"; done; unset IFS
fi
plan H1B "Appendix B — Source files"
plan SOURCES ""

# Ficheiros e pastas existentes (para validar os links)
EXISTS="$(find . \( -name .git -o -name node_modules -o -name target \) -prune -o -print | sed 's#^\./##; s#^\.$##')"

TMP="$OUT.tmp"
printf '%s' "$PLAN" | awk -v today="$(date +%Y-%m-%d)" -v exists="$EXISTS" -F '\t' '
function warn(msg) { print "WARNING: " msg | "cat 1>&2" }

# ---------------------------------------------------------------- slugs e âncoras
function aslug(t, full,   i, c, out, prev) { # full: sem corte aos 60 caracteres (para comparar títulos)
  t = tolower(t); out = ""; prev = 0
  for (i = 1; i <= length(t); i++) {
    c = substr(t, i, 1)
    if (c ~ /[a-z0-9]/) { out = out c; prev = 0 }
    else if (!prev && out != "") { out = out "-"; prev = 1 }
  }
  sub(/-+$/, "", out)
  if (!full && length(out) > 60) { out = substr(out, 1, 60); sub(/-+$/, "", out) }
  if (out == "") out = "section"
  return out
}
function strip_links(t,   seg, p, inner) {
  while (match(t, /!?\[[^]]*\]\([^)]*\)/)) {
    seg = substr(t, RSTART, RLENGTH)
    p = index(seg, "](")
    inner = substr(seg, 1, p - 1); sub(/^!?\[/, "", inner)
    t = substr(t, 1, RSTART - 1) inner substr(t, RSTART + RLENGTH)
  }
  return t
}
# Âncora ao estilo GitHub (para traduzir os links #fragmento que os documentos já usam)
function ghslug(t,   i, n, c, b, len, cp, out) {
  t = tolower(strip_links(t)); out = ""; n = length(t); i = 1
  while (i <= n) {
    c = substr(t, i, 1); b = ORD[c]
    if (b < 128) {
      if (c ~ /[a-z0-9_-]/) out = out c
      else if (c == " ") out = out "-"
      i++
    } else {
      len = (b >= 240) ? 4 : (b >= 224) ? 3 : (b >= 192) ? 2 : 1
      cp = (len == 2) ? (b - 192) * 64 + (ORD[substr(t, i + 1, 1)] - 128) : -1
      if (cp >= 192 && cp <= 591 && cp != 215 && cp != 247) out = out substr(t, i, len)
      i += len
    }
  }
  return out
}
function uniq(id,   k) {
  if (!(id in USED)) { USED[id] = 1; return id }
  k = 2; while ((id "-" k) in USED) k++
  USED[id "-" k] = 1; return id "-" k
}
function newid(prefix, text) { IDS[++NIDS] = uniq(prefix aslug(text)); return IDS[NIDS] }
function nextid() { return IDS[++CID] }

# ---------------------------------------------------------------- markdown
function fence(line,   s, k, c, n, rest) { # 1 se a linha abre/fecha um bloco de código
  s = line; k = 0
  while (k < 3 && substr(s, 1, 1) == " ") { s = substr(s, 2); k++ }
  c = substr(s, 1, 1)
  if (c != "`" && c != "~") return 0
  n = 0; while (substr(s, n + 1, 1) == c) n++
  if (n < 3) return 0
  rest = substr(s, n + 1)
  if (!INF) {
    if (c == "`" && index(rest, "`")) return 0
    INF = 1; FCH = c; FLEN = n; return 1
  }
  if (c == FCH && n >= FLEN && rest ~ /^[ \t]*$/) { INF = 0; return 1 }
  return 0
}
function heading(line,   s, k, n, t) { # devolve o nível (HTEXT = texto) ou 0
  s = line; k = 0
  while (k < 3 && substr(s, 1, 1) == " ") { s = substr(s, 2); k++ }
  n = 0; while (substr(s, n + 1, 1) == "#") n++
  if (n < 1 || n > 6) return 0
  t = substr(s, n + 1)
  if (t != "" && t !~ /^[ \t]/) return 0
  sub(/^[ \t]+/, "", t)
  if (t ~ /^#+[ \t]*$/) t = ""
  else sub(/[ \t]+#+[ \t]*$/, "", t)
  sub(/[ \t]+$/, "", t)
  HTEXT = t
  return n
}
function dirname(p) { if (index(p, "/")) { sub(/\/[^\/]*$/, "", p); return p } return "" }
function basename(p) { sub(/^.*\//, "", p); return p }
function normpath(p,   parts, n, i, st, k, out) {
  n = split(p, parts, "/"); k = 0
  for (i = 1; i <= n; i++) {
    if (parts[i] == "" || parts[i] == ".") continue
    if (parts[i] == "..") { if (k > 0 && st[k] != "..") k--; else st[++k] = ".." }
    else st[++k] = parts[i]
  }
  out = ""
  for (i = 1; i <= k; i++) out = out (i > 1 ? "/" : "") st[i]
  return out
}
function reltarget(r) { # caminho relativo à raiz do repositório → relativo a docs/
  if (substr(r, 1, 5) == "docs/") return substr(r, 6)
  if (r == "docs") return "."
  return "../" r
}
function rewrite_target(t, isimg, path,   url, extra, sp, frag, i, r, slash, key, d, c1, c2, out) {
  sp = index(t, " "); url = t; extra = ""
  if (sp) { url = substr(t, 1, sp - 1); extra = substr(t, sp) }
  if (url == "" || url ~ /^[A-Za-z][A-Za-z0-9+.-]*:/ || substr(url, 1, 1) == "/") return t
  frag = ""; i = index(url, "#")
  if (i) { frag = substr(url, i + 1); url = substr(url, 1, i - 1) }
  if (url == "") {
    key = path "#" frag
    if (key in MAP) return "#" MAP[key] extra
    warn(path ": anchor #" frag " not found; linked to the start of the file instead")
    return "#" FID[path] extra
  }
  slash = (url ~ /\/$/)
  r = normpath(dirname(path) "/" url)
  if (r in FID) {
    if (frag != "") {
      key = r "#" frag
      if (key in MAP) return "#" MAP[key] extra
      warn(path ": anchor " url "#" frag " not found; linked to the start of that file instead")
    }
    return "#" FID[r] extra
  }
  if (isimg && r ~ /\.puml$/) {
    d = dirname(r); c2 = substr(r, 1, length(r) - 5) ".svg"; c1 = ""
    if (basename(d) == "puml") c1 = dirname(d) "/svg/" basename(c2)
    if (c1 != "" && (c1 in EX)) r = c1
    else if (c2 in EX) r = c2
    else warn(path ": diagram " url " has no .svg; reference kept to the .puml")
  }
  if (!(r in EX)) warn(path ": link target not found: " url)
  out = reltarget(r)
  if (slash) out = out "/"
  if (frag != "") out = out "#" frag
  return out extra
}
function rewrite_line(line, path,   out, seg, p) {
  out = ""
  while (match(line, /!?\[[^]]*\]\([^)]*\)/)) {
    seg = substr(line, RSTART, RLENGTH)
    out = out substr(line, 1, RSTART - 1)
    line = substr(line, RSTART + RLENGTH)
    p = index(seg, "](")
    out = out substr(seg, 1, p + 1) rewrite_target(substr(seg, p + 2, length(seg) - p - 2), substr(seg, 1, 1) == "!", path) ")"
  }
  return out line
}
function load(path,   l, n) {
  n = 0
  while ((getline l < path) > 0) {
    sub(/\r$/, "", l)
    if (n == 0) sub(/^\357\273\277/, "", l)
    FL[path, ++n] = l
  }
  close(path); FN[path] = n
}

# ---------------------------------------------------------------- JSON (coleções Postman)
function skipws() { while (substr(S, POS, 1) ~ /^[ \t\r\n]$/) POS++ }
function hexval(h,   i, v) { v = 0; for (i = 1; i <= length(h); i++) v = v * 16 + index("0123456789abcdef", tolower(substr(h, i, 1))) - 1; return v }
function utf8(cp) {
  if (cp < 128) return sprintf("%c", cp)
  if (cp < 2048) return sprintf("%c%c", 192 + int(cp / 64), 128 + cp % 64)
  if (cp < 65536) return sprintf("%c%c%c", 224 + int(cp / 4096), 128 + int(cp / 64) % 64, 128 + cp % 64)
  return sprintf("%c%c%c%c", 240 + int(cp / 262144), 128 + int(cp / 4096) % 64, 128 + int(cp / 64) % 64, 128 + cp % 64)
}
function jstring(   out, c, e, cp, lo) {
  POS++; out = ""
  while (1) {
    c = substr(S, POS, 1)
    if (c == "" || c == "\"") { POS++; return out }
    if (c != "\\") { out = out c; POS++; continue }
    e = substr(S, POS + 1, 1); POS += 2
    if (e == "n") out = out "\n"; else if (e == "t") out = out "\t"; else if (e == "r") out = out "\r"
    else if (e == "b") out = out sprintf("%c", 8); else if (e == "f") out = out sprintf("%c", 12)
    else if (e == "u") {
      cp = hexval(substr(S, POS, 4)); POS += 4
      if (cp >= 55296 && cp < 56320 && substr(S, POS, 2) == "\\u") {
        lo = hexval(substr(S, POS + 2, 4)); POS += 6
        cp = 65536 + (cp - 55296) * 1024 + (lo - 56320)
      }
      out = out utf8(cp)
    } else out = out e
  }
}
function jvalue(path,   c, key, idx, lit) {
  skipws(); c = substr(S, POS, 1)
  if (c == "{") {
    POS++; JT[path] = "o"; skipws()
    if (substr(S, POS, 1) == "}") { POS++; return }
    while (1) {
      skipws(); key = jstring(); skipws(); POS++
      jvalue(path "." key); skipws()
      c = substr(S, POS, 1); POS++
      if (c != ",") return
    }
  }
  if (c == "[") {
    POS++; JT[path] = "a"; idx = 0; skipws()
    if (substr(S, POS, 1) == "]") { POS++; return }
    while (1) {
      jvalue(path "[" idx "]"); idx++; skipws()
      c = substr(S, POS, 1); POS++
      if (c != ",") return
    }
  }
  if (c == "\"") { JT[path] = "s"; JV[path] = jstring(); return }
  lit = ""
  while ((c = substr(S, POS, 1)) != "" && c !~ /^[,}\] \t\r\n]$/) { lit = lit c; POS++ }
  JT[path] = "l"; JV[path] = lit
}
function cell(s) { gsub(/[\r\n]+/, " ", s); gsub(/\|/, "\\|", s); return s }
function pmwalk(file, p, folder,   i, it, name, url) {
  for (i = 0; ((p ".item[" i "]") in JT); i++) {
    it = p ".item[" i "]"; name = JV[it ".name"]
    if ((it ".item") in JT) { pmwalk(file, it, folder == "" ? name : folder " / " name); continue }
    url = (JT[it ".request.url"] == "s") ? JV[it ".request.url"] : JV[it ".request.url.raw"]
    PMR[file, ++PMN[file]] = "| " (folder == "" ? "—" : cell(folder)) " | " cell(name) " | " cell(JV[it ".request.method"]) " | " (url == "" ? "" : "`" cell(url) "`") " |"
  }
}
function pmload(file,   l) {
  S = ""; while ((getline l < file) > 0) S = S l "\n"
  close(file); sub(/^\357\273\277/, "", S)
  split("", JT); split("", JV); POS = 1; jvalue("")
  PMNAME[file] = JV[".info.name"]; PMN[file] = 0
  pmwalk(file, "", "")
}

# ---------------------------------------------------------------- saída
function out(l) { B[++NB] = l }
function emith(level, text, id) {
  out(""); out("<a id=\"" id "\"></a>"); out("")
  if (level <= 6) { out(substr("######", 1, level) " " text) } else out("**" text "**")
  if (level <= 3) { TL[++NT] = level; TT[NT] = strip_links(text); TI[NT] = id }
}
function pagebreak(   k) { # nunca duas quebras de página seguidas
  for (k = NB; k > 0 && B[k] == ""; k--) ;
  if (k > 0 && B[k] == PB) return
  out(""); out(PB)
}
function firsthead(path,   j, l) { # texto do primeiro título do ficheiro ("" se não tiver)
  INF = 0
  for (j = 1; j <= FN[path]; j++) {
    l = FL[path, j]
    if (fence(l) || INF) continue
    if (heading(l)) return strip_links(HTEXT)
  }
  return ""
}

{ TYPE[NR] = $1; ARG[NR] = $2 }

END {
  for (i = 1; i < 256; i++) ORD[sprintf("%c", i)] = i
  n = split(exists, ex, "\n"); for (i = 1; i <= n; i++) EX[ex[i]] = 1

  PB = "<div style=\"page-break-before: always;\"></div>"

  # Passo 0: carregar os ficheiros. Um título de capítulo seguido de um ficheiro cujo primeiro título
  # é o mesmo (ignorando maiúsculas e pontuação) não é escrito: o título do ficheiro toma o seu lugar.
  for (r = 1; r <= NR; r++) if (TYPE[r] == "FILE") load(ARG[r])
  for (r = 1; r < NR; r++) {
    if ((TYPE[r] == "H2" || TYPE[r] == "H2B") && TYPE[r + 1] == "FILE") {
      h = firsthead(ARG[r + 1])
      if (h != "" && aslug(h, 1) == aslug(ARG[r], 1)) { SUPPRESS[r] = 1; PROMOTE[ARG[r + 1]] = 1 }
    }
  }

  # Passo 1: todas as âncoras, por ordem do documento
  for (r = 1; r <= NR; r++) {
    t = TYPE[r]; a = ARG[r]
    if (t == "H1B" || t == "H2" || t == "H2B") { if (!(r in SUPPRESS)) newid("sec-", a) }
    else if (t == "FILE") {
      FID[a] = newid("src-", substr(a, 6, length(a) - 8)); INF = 0
      for (j = 1; j <= FN[a]; j++) {
        l = FL[a, j]
        if (fence(l) || INF) continue
        if (heading(l)) {
          id = newid("sec-", strip_links(HTEXT)); g = ghslug(HTEXT)
          if ((a, g) in GHC) { GHC[a, g]++; g = g "-" GHC[a, g] } else GHC[a, g] = 0
          MAP[a "#" g] = id
        }
      }
    } else if (t == "PM") { pmload(a); newid("sec-", PMNAME[a]) }
  }

  # Passo 2: o corpo do documento
  for (r = 1; r <= NR; r++) {
    t = TYPE[r]; a = ARG[r]
    # Logo a seguir ao título de uma Parte não há quebra de página (a Parte não fica sozinha numa página)
    brk = !(r > 1 && TYPE[r - 1] == "H1B")
    if (t == "H1B") { if (brk) pagebreak(); emith(1, a, nextid()); part = a; chap = "" }
    else if (t == "H2") { chap = a; if (!(r in SUPPRESS)) emith(2, a, nextid()) }
    else if (t == "H2B") { if (brk) pagebreak(); chap = a; if (!(r in SUPPRESS)) emith(2, a, nextid()) }
    else if (t == "OWNER") { out(""); out("**Owner:** " a) }
    else if (t == "FILE") {
      nextid(); SRC[++NS] = a; SPART[NS] = part; SCHAP[NS] = chap
      out(""); out("<a id=\"" FID[a] "\"></a>"); out(""); out("*Source: " a "*"); out("")
      INF = 0; promote = (a in PROMOTE)
      for (j = 1; j <= FN[a]; j++) {
        l = FL[a, j]
        if (fence(l) || INF) { out(l); continue }
        lv = heading(l)
        if (lv) { emith(promote ? 2 : lv + 2, rewrite_line(HTEXT, a), nextid()); promote = 0 }
        else out(rewrite_line(l, a))
      }
    } else if (t == "PM") {
      emith(2, PMNAME[a], nextid())
      out(""); out("*Source: [" a "](" reltarget(a) ")*"); out("")
      out("| Folder | Request | Method | URL |"); out("|---|---|---|---|")
      for (j = 1; j <= PMN[a]; j++) out(PMR[a, j])
    } else if (t == "SOURCES") {
      out(""); out("| # | Part | Chapter | Source file |"); out("|---|---|---|---|")
      for (j = 1; j <= NS; j++) out("| " j " | " SPART[j] " | " SCHAP[j] " | [" SRC[j] "](#" FID[SRC[j]] ") |")
    }
  }

  # Capa, índice e corpo
  print "# AISafe Flight Management System — Distributed Architecture (SIDIS 2026/2027, Assignment 1)"
  print ""
  print "**ISEP — Instituto Superior de Engenharia do Porto**"
  print ""
  print "**Generated:** " today
  print ""
  print "| Student | Number | Work package |"
  print "|---|---|---|"
  print "| Tiago Caetano | 1241945 | WP3 Flight Routes |"
  print "| Tiago Moreira | 1240883 | WP1 Aircraft Management |"
  print "| Afonso Ribeiro | 1240857 | WP2 Airports |"
  print "| Diogo Fernandes | 1240632 | WP4 Maintenance |"
  print ""
  print "<div style=\"page-break-before: always;\"></div>"
  print ""
  print "## Table of contents"
  print ""
  for (i = 1; i <= NT; i++) print substr("    ", 1, 2 * (TL[i] - 1)) "- [" TT[i] "](#" TI[i] ")"
  for (i = 1; i <= NB; i++) print B[i]
  close("cat 1>&2")
}' > "$TMP"
mv "$TMP" "$OUT"
echo "Generated $OUT ($(wc -l < "$OUT" | tr -d ' ') lines)" >&2
