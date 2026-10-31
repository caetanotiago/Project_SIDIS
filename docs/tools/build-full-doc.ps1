# Gera docs/Documentation.md: toda a documentação (docs/**/*.md) num único documento, por ordem
# C4+1, com índice, âncoras únicas, links reescritos e apêndices (coleções Postman, lista de ficheiros).
# O conteúdo dos ficheiros é incluído tal como está; só os níveis dos títulos e os caminhos dos links mudam.
# Avisos (ficheiros sem lugar, diagramas sem .svg, links partidos) só no terminal.
# Uso (de qualquer pasta):  powershell -ExecutionPolicy Bypass -File docs\tools\build-full-doc.ps1
# Mesma saída que docs/tools/build-full-doc.sh (mesmo algoritmo). Requisitos: Windows PowerShell 5.1+.
# Nota: o código não tem caracteres não-ASCII (o PowerShell 5.1 lê .ps1 sem BOM como ANSI);
# o travessão e afins são construídos com [char].
$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$OutRel = "docs/Documentation.md"
$Services = @("aircraftmanagement", "airports", "flightroutes", "maintenance")
$EM = [string][char]0x2014
$Ordinal = [StringComparer]::Ordinal
$LinkRe = New-Object System.Text.RegularExpressions.Regex '!?\[[^\]]*\]\([^)]*\)'

function New-Dict([string]$valueType = "string") {
    return New-Object "System.Collections.Generic.Dictionary[string,$valueType]" ($Ordinal)
}
function Sort-Ordinal([string[]]$items) {
    if (-not $items) { return @() }
    $a = [string[]]$items.Clone(); [Array]::Sort($a, $Ordinal); return $a
}

# ---------------------------------------------------------------- ficheiros do repositório
# Equivalente a: find . ( -name .git -o -name node_modules -o -name target ) -prune -o -print
$Exists = New-Object System.Collections.Generic.HashSet[string] ($Ordinal)
[void]$Exists.Add("")
$AllFiles = New-Object System.Collections.Generic.List[string]
$stack = New-Object System.Collections.Generic.Stack[string]
$stack.Push("")
while ($stack.Count -gt 0) {
    $rel = $stack.Pop()
    $dir = if ($rel) { Join-Path $Root $rel } else { $Root }
    foreach ($e in (Get-ChildItem -LiteralPath $dir -Force)) {
        if ($e.Name -ceq ".git" -or $e.Name -ceq "node_modules" -or $e.Name -ceq "target") { continue }
        $p = if ($rel) { "$rel/$($e.Name)" } else { $e.Name }
        [void]$Exists.Add($p)
        if ($e.PSIsContainer) { $stack.Push($p) } else { $AllFiles.Add($p) }
    }
}

# ---------------------------------------------------------------- plano do documento
$All = Sort-Ordinal @($AllFiles | Where-Object {
        $_.StartsWith("docs/", [StringComparison]::Ordinal) -and $_.EndsWith(".md", [StringComparison]::Ordinal) -and
        -not $_.StartsWith("docs/_template/", [StringComparison]::Ordinal) -and
        -not $_.StartsWith("docs/tools/", [StringComparison]::Ordinal) -and $_ -cne $OutRel })
$AllSet = New-Object System.Collections.Generic.HashSet[string] ([string[]]$All, $Ordinal)
$Placed = New-Object System.Collections.Generic.HashSet[string] ($Ordinal)
$script:Cur = New-Object System.Collections.Generic.List[string]

function Add-Exact([string]$f) {
    if ($AllSet.Contains($f) -and $Placed.Add($f)) { $script:Cur.Add($f) }
}
function Add-Under([string]$prefix) {
    foreach ($f in $All) { if ($f.StartsWith($prefix, [StringComparison]::Ordinal)) { Add-Exact $f } }
}
function Take() { $r = $script:Cur.ToArray(); $script:Cur = New-Object System.Collections.Generic.List[string]; return , $r }

Add-Exact "docs/README.md"; $Intro = Take
Add-Under "docs/1-context/"; $Context = Take
foreach ($f in @("Containers", "Ports", "Integration-Contracts")) { Add-Exact "docs/2-containers/$f.md" }
Add-Under "docs/2-containers/"; $Containers = Take
Add-Under "docs/3-components/common/"; $CommonL3 = Take
Add-Under "docs/4-code/common/"; $CommonL4 = Take
Add-Under "docs/5-scenarios/common/"; $CommonL5 = Take
$SvcFiles = @{}
foreach ($svc in $Services) {
    foreach ($level in @("3-components", "4-code", "5-scenarios")) { Add-Under "docs/$level/$svc/" }
    $SvcFiles[$svc] = Take
    if ($SvcFiles[$svc].Count -eq 0) { Write-Warning "no documentation found for service '$svc'; its chapter was left out" }
}
Add-Under "docs/"; $Extra = Take
foreach ($f in $Extra) { Write-Warning "$f has no known place; included in Part I, chapter 'Additional documents'" }

$Plan = New-Object System.Collections.Generic.List[object]
function Plan([string]$t, [string]$a) { $Plan.Add(@($t, $a)) }
function Chapter([string]$t, [string]$title, [string[]]$files) {
    if (-not $files -or $files.Count -eq 0) { return }
    Plan $t $title
    foreach ($f in $files) { Plan "FILE" $f }
}

Plan "H1B" "Part I $EM System"
Chapter "H2" "Introduction and conventions" $Intro
Chapter "H2" "C4 Level 1 $EM System context" $Context
Chapter "H2" "C4 Level 2 $EM Containers" $Containers
Chapter "H2" "Additional documents" $Extra
if (($CommonL3.Count + $CommonL4.Count + $CommonL5.Count) -gt 0) {
    Plan "H1B" ('Part II ' + $EM + ' Shared infrastructure (`common`)')
    Chapter "H2" "C4 Level 3 $EM Components" $CommonL3
    Chapter "H2" "C4 Level 4 $EM Code" $CommonL4
    Chapter "H2" "Scenarios (+1)" $CommonL5
}
Plan "H1B" "Part III $EM Services"
$SvcInfo = @{
    "aircraftmanagement" = @("Aircraft Management (WP1)", "Tiago Moreira (1240883) $EM WP1 Aircraft Management")
    "airports"           = @("Airports (WP2)", "Afonso Ribeiro (1240857) $EM WP2 Airports")
    "flightroutes"       = @("Flight Routes (WP3)", "Tiago Caetano (1241945) $EM WP3 Flight Routes")
    "maintenance"        = @("Maintenance (WP4)", "Diogo Fernandes (1240632) $EM WP4 Maintenance")
}
foreach ($svc in $Services) {
    if ($SvcFiles[$svc].Count -eq 0) { continue }
    Plan "H2B" $SvcInfo[$svc][0]
    Plan "OWNER" $SvcInfo[$svc][1]
    foreach ($f in $SvcFiles[$svc]) { Plan "FILE" $f }
}
$Postman = Sort-Ordinal @($AllFiles | Where-Object { $_.EndsWith(".postman_collection.json", [StringComparison]::Ordinal) })
if ($Postman.Count -gt 0) {
    Plan "H1B" "Appendix A $EM Postman collections"
    foreach ($f in $Postman) { Plan "PM" $f }
}
Plan "H1B" "Appendix B $EM Source files"
Plan "SOURCES" ""

# ---------------------------------------------------------------- slugs e âncoras
function ASlug([string]$t, [bool]$full = $false) { # full: sem corte aos 60 caracteres (para comparar títulos)
    $sb = New-Object System.Text.StringBuilder; $prev = $false
    foreach ($ch in $t.ToCharArray()) {
        $c = [int]$ch
        if ($c -ge 65 -and $c -le 90) { $c += 32 }
        if (($c -ge 97 -and $c -le 122) -or ($c -ge 48 -and $c -le 57)) { [void]$sb.Append([char]$c); $prev = $false }
        elseif (-not $prev -and $sb.Length -gt 0) { [void]$sb.Append('-'); $prev = $true }
    }
    $s = $sb.ToString().TrimEnd('-')
    if (-not $full -and $s.Length -gt 60) { $s = $s.Substring(0, 60).TrimEnd('-') }
    if ($s -eq "") { $s = "section" }
    return $s
}
function Strip-Links([string]$t) {
    while ($true) {
        $m = $LinkRe.Match($t)
        if (-not $m.Success) { return $t }
        $seg = $m.Value
        $p = $seg.IndexOf("](", [StringComparison]::Ordinal)
        $inner = $seg.Substring(0, $p) -creplace '^!?\[', ''
        $t = $t.Substring(0, $m.Index) + $inner + $t.Substring($m.Index + $m.Length)
    }
}
# Âncora ao estilo GitHub (para traduzir os links #fragmento que os documentos já usam)
function GhSlug([string]$t) {
    $sb = New-Object System.Text.StringBuilder
    foreach ($ch in (Strip-Links $t).ToCharArray()) {
        $c = [int]$ch
        if ($c -lt 128) {
            if ($c -ge 65 -and $c -le 90) { $c += 32 }
            if (($c -ge 97 -and $c -le 122) -or ($c -ge 48 -and $c -le 57) -or $c -eq 95 -or $c -eq 45) { [void]$sb.Append([char]$c) }
            elseif ($c -eq 32) { [void]$sb.Append('-') }
        } elseif ($c -ge 192 -and $c -le 591 -and $c -ne 215 -and $c -ne 247) { [void]$sb.Append($ch) }
    }
    return $sb.ToString()
}
$Used = New-Object System.Collections.Generic.HashSet[string] ($Ordinal)
$Ids = New-Object System.Collections.Generic.List[string]
$script:Cid = 0
function Uniq([string]$id) {
    if ($Used.Add($id)) { return $id }
    $k = 2; while ($Used.Contains("$id-$k")) { $k++ }
    [void]$Used.Add("$id-$k"); return "$id-$k"
}
function New-Id([string]$prefix, [string]$text) { $id = Uniq ($prefix + (ASlug $text)); $Ids.Add($id); return $id }
function Next-Id() { $id = $Ids[$script:Cid]; $script:Cid++; return $id }

# ---------------------------------------------------------------- markdown
$script:Inf = $false; $script:Fch = ''; $script:Flen = 0; $script:HText = ""
function Test-Fence([string]$line) { # $true se a linha abre/fecha um bloco de código
    $s = $line; $k = 0
    while ($k -lt 3 -and $s.StartsWith(" ", [StringComparison]::Ordinal)) { $s = $s.Substring(1); $k++ }
    if ($s.Length -eq 0) { return $false }
    $c = $s[0]
    if ($c -ne [char]'`' -and $c -ne [char]'~') { return $false }
    $n = 0; while ($n -lt $s.Length -and $s[$n] -eq $c) { $n++ }
    if ($n -lt 3) { return $false }
    $rest = $s.Substring($n)
    if (-not $script:Inf) {
        if ($c -eq [char]'`' -and $rest.Contains('`')) { return $false }
        $script:Inf = $true; $script:Fch = $c; $script:Flen = $n; return $true
    }
    if ($c -eq $script:Fch -and $n -ge $script:Flen -and $rest -cmatch '^[ \t]*$') { $script:Inf = $false; return $true }
    return $false
}
function Get-HeadingLevel([string]$line) { # devolve o nível ($script:HText = texto) ou 0
    $s = $line; $k = 0
    while ($k -lt 3 -and $s.StartsWith(" ", [StringComparison]::Ordinal)) { $s = $s.Substring(1); $k++ }
    $n = 0; while ($n -lt $s.Length -and $s[$n] -eq [char]'#') { $n++ }
    if ($n -lt 1 -or $n -gt 6) { return 0 }
    $t = $s.Substring($n)
    if ($t -ne "" -and $t -cnotmatch '^[ \t]') { return 0 }
    $t = $t -creplace '^[ \t]+', ''
    if ($t -cmatch '^#+[ \t]*$') { $t = "" } else { $t = $t -creplace '[ \t]+#+[ \t]*$', '' }
    $t = $t -creplace '[ \t]+$', ''
    $script:HText = $t
    return $n
}
function DirName([string]$p) { $i = $p.LastIndexOf('/'); if ($i -ge 0) { return $p.Substring(0, $i) } return "" }
function BaseName([string]$p) { $i = $p.LastIndexOf('/'); return $p.Substring($i + 1) }
function Norm-Path([string]$p) {
    $st = New-Object System.Collections.Generic.List[string]
    foreach ($part in $p.Split('/')) {
        if ($part -eq "" -or $part -eq ".") { continue }
        if ($part -eq "..") { if ($st.Count -gt 0 -and $st[$st.Count - 1] -ne "..") { $st.RemoveAt($st.Count - 1) } else { $st.Add("..") } }
        else { $st.Add($part) }
    }
    return ($st -join "/")
}
function Rel-Target([string]$r) { # caminho relativo à raiz do repositório -> relativo a docs/
    if ($r.StartsWith("docs/", [StringComparison]::Ordinal)) { return $r.Substring(5) }
    if ($r -ceq "docs") { return "." }
    return "../$r"
}
function Warn([string]$msg) { Write-Warning $msg }
function Rewrite-Target([string]$t, [bool]$isImg, [string]$path) {
    $sp = $t.IndexOf(' '); $url = $t; $extra = ""
    if ($sp -ge 0) { $url = $t.Substring(0, $sp); $extra = $t.Substring($sp) }
    if ($url -eq "" -or $url -cmatch '^[A-Za-z][A-Za-z0-9+.-]*:' -or $url.StartsWith("/", [StringComparison]::Ordinal)) { return $t }
    $frag = ""; $i = $url.IndexOf('#')
    if ($i -ge 0) { $frag = $url.Substring($i + 1); $url = $url.Substring(0, $i) }
    if ($url -eq "") {
        $key = "$path#$frag"
        if ($Map.ContainsKey($key)) { return "#" + $Map[$key] + $extra }
        Warn "${path}: anchor #$frag not found; linked to the start of the file instead"
        return "#" + $Fid[$path] + $extra
    }
    $slash = $url.EndsWith("/", [StringComparison]::Ordinal)
    $r = Norm-Path ((DirName $path) + "/" + $url)
    if ($Fid.ContainsKey($r)) {
        if ($frag -ne "") {
            $key = "$r#$frag"
            if ($Map.ContainsKey($key)) { return "#" + $Map[$key] + $extra }
            Warn "${path}: anchor $url#$frag not found; linked to the start of that file instead"
        }
        return "#" + $Fid[$r] + $extra
    }
    if ($isImg -and $r.EndsWith(".puml", [StringComparison]::Ordinal)) {
        $d = DirName $r; $c2 = $r.Substring(0, $r.Length - 5) + ".svg"; $c1 = ""
        if ((BaseName $d) -ceq "puml") { $c1 = (DirName $d) + "/svg/" + (BaseName $c2) }
        if ($c1 -ne "" -and $Exists.Contains($c1)) { $r = $c1 }
        elseif ($Exists.Contains($c2)) { $r = $c2 }
        else { Warn "${path}: diagram $url has no .svg; reference kept to the .puml" }
    }
    if (-not $Exists.Contains($r)) { Warn "${path}: link target not found: $url" }
    $o = Rel-Target $r
    if ($slash) { $o += "/" }
    if ($frag -ne "") { $o += "#$frag" }
    return $o + $extra
}
function Rewrite-Line([string]$line, [string]$path) {
    $sb = New-Object System.Text.StringBuilder
    while ($true) {
        $m = $LinkRe.Match($line)
        if (-not $m.Success) { break }
        $seg = $m.Value
        [void]$sb.Append($line.Substring(0, $m.Index))
        $line = $line.Substring($m.Index + $m.Length)
        $p = $seg.IndexOf("](", [StringComparison]::Ordinal)
        [void]$sb.Append($seg.Substring(0, $p + 2))
        [void]$sb.Append((Rewrite-Target $seg.Substring($p + 2, $seg.Length - $p - 3) ($seg[0] -eq [char]'!') $path))
        [void]$sb.Append(")")
    }
    [void]$sb.Append($line)
    return $sb.ToString()
}
$Utf8 = New-Object System.Text.UTF8Encoding $false
function Read-Lines([string]$rel) {
    $text = [IO.File]::ReadAllText((Join-Path $Root $rel), $Utf8)
    if ($text.StartsWith([string][char]0xFEFF, [StringComparison]::Ordinal)) { $text = $text.Substring(1) }
    if ($text -eq "") { return , @() }
    $lines = $text.Split([char]"`n")
    if ($text.EndsWith("`n", [StringComparison]::Ordinal)) { $lines = $lines[0..($lines.Length - 2)] }
    for ($i = 0; $i -lt $lines.Length; $i++) { if ($lines[$i].EndsWith("`r", [StringComparison]::Ordinal)) { $lines[$i] = $lines[$i].Substring(0, $lines[$i].Length - 1) } }
    return , [string[]]$lines
}

# ---------------------------------------------------------------- coleções Postman
function Cell([string]$s) { return (($s -creplace '[\r\n]+', ' ') -replace '\|', '\|') }
function Pm-Walk($node, [string]$folder, $rows) {
    if ($null -eq $node -or -not ($node.PSObject.Properties.Name -ccontains "item")) { return }
    foreach ($it in @($node.item)) {
        if ($null -eq $it) { continue }
        $name = if ($null -ne $it.name) { [string]$it.name } else { "" }
        if ($it.PSObject.Properties.Name -ccontains "item") {
            Pm-Walk $it $(if ($folder -eq "") { $name } else { "$folder / $name" }) $rows; continue
        }
        $u = $it.request.url
        $url = if ($u -is [string]) { $u } elseif ($null -ne $u -and $null -ne $u.raw) { [string]$u.raw } else { "" }
        $method = if ($null -ne $it.request.method) { [string]$it.request.method } else { "" }
        $f = if ($folder -eq "") { $EM } else { Cell $folder }
        $rows.Add("| $f | $(Cell $name) | $(Cell $method) | $(if ($url -eq '') { '' } else { '`' + (Cell $url) + '`' }) |")
    }
}

# ---------------------------------------------------------------- passo 0: ficheiros e títulos repetidos
# Um título de capítulo seguido de um ficheiro cujo primeiro título é o mesmo (ignorando maiúsculas e
# pontuação) não é escrito: o título do ficheiro toma o seu lugar.
$Fid = New-Dict; $Map = New-Dict; $Ghc = New-Dict "int"
$FileLines = New-Dict "object"; $PmName = New-Dict; $PmRows = New-Dict "object"
foreach ($e in $Plan) { if ($e[0] -ceq "FILE") { $FileLines[$e[1]] = Read-Lines $e[1] } }
function First-Heading([string]$path) { # texto do primeiro título do ficheiro ("" se não tiver)
    $script:Inf = $false
    foreach ($l in $FileLines[$path]) {
        if ((Test-Fence $l) -or $script:Inf) { continue }
        if ((Get-HeadingLevel $l) -gt 0) { return (Strip-Links $script:HText) }
    }
    return ""
}
$Suppress = New-Object System.Collections.Generic.HashSet[int]
$Promote = New-Object System.Collections.Generic.HashSet[string] ($Ordinal)
for ($r = 0; $r -lt $Plan.Count - 1; $r++) {
    if (($Plan[$r][0] -ceq "H2" -or $Plan[$r][0] -ceq "H2B") -and $Plan[$r + 1][0] -ceq "FILE") {
        $h = First-Heading $Plan[$r + 1][1]
        if ($h -ne "" -and (ASlug $h $true) -ceq (ASlug $Plan[$r][1] $true)) { [void]$Suppress.Add($r); [void]$Promote.Add($Plan[$r + 1][1]) }
    }
}

# ---------------------------------------------------------------- passo 1: todas as âncoras, por ordem
for ($r = 0; $r -lt $Plan.Count; $r++) {
    $t = $Plan[$r][0]; $a = $Plan[$r][1]
    if ($t -ceq "H1B" -or $t -ceq "H2" -or $t -ceq "H2B") { if (-not $Suppress.Contains($r)) { [void](New-Id "sec-" $a) } }
    elseif ($t -ceq "FILE") {
        $Fid[$a] = New-Id "src-" $a.Substring(5, $a.Length - 8)
        $lines = $FileLines[$a]; $script:Inf = $false
        foreach ($l in $lines) {
            if ((Test-Fence $l) -or $script:Inf) { continue }
            if ((Get-HeadingLevel $l) -gt 0) {
                $id = New-Id "sec-" (Strip-Links $script:HText); $g = GhSlug $script:HText
                $gk = "$a`n$g"
                if ($Ghc.ContainsKey($gk)) { $Ghc[$gk]++; $g = "$g-$($Ghc[$gk])" } else { $Ghc[$gk] = 0 }
                $Map["$a#$g"] = $id
            }
        }
    } elseif ($t -ceq "PM") {
        $json = [IO.File]::ReadAllText((Join-Path $Root $a), $Utf8)
        if ($json.StartsWith([string][char]0xFEFF, [StringComparison]::Ordinal)) { $json = $json.Substring(1) }
        $c = ConvertFrom-Json -InputObject $json   # (em pipeline o PowerShell 5.1 falha com este ficheiro)
        $PmName[$a] = if ($null -ne $c.info -and $null -ne $c.info.name) { [string]$c.info.name } else { "" }
        $rows = New-Object System.Collections.Generic.List[string]
        Pm-Walk $c "" $rows
        $PmRows[$a] = $rows
        [void](New-Id "sec-" $PmName[$a])
    }
}

# ---------------------------------------------------------------- passo 2: corpo do documento
$B = New-Object System.Collections.Generic.List[string]
$Toc = New-Object System.Collections.Generic.List[string]
function Emit-H([int]$level, [string]$text, [string]$id) {
    $B.Add(""); $B.Add("<a id=`"$id`"></a>"); $B.Add("")
    if ($level -le 6) { $B.Add(("#" * $level) + " " + $text) } else { $B.Add("**$text**") }
    if ($level -le 3) { $Toc.Add((" " * (2 * ($level - 1))) + "- [" + (Strip-Links $text) + "](#$id)") }
}
$PB = '<div style="page-break-before: always;"></div>'
function Page-Break() { # nunca duas quebras de página seguidas
    $k = $B.Count - 1
    while ($k -ge 0 -and $B[$k] -eq "") { $k-- }
    if ($k -ge 0 -and $B[$k] -ceq $PB) { return }
    $B.Add(""); $B.Add($PB)
}
$Sources = New-Object System.Collections.Generic.List[object]
$part = ""; $chap = ""
for ($r = 0; $r -lt $Plan.Count; $r++) {
    $t = $Plan[$r][0]; $a = $Plan[$r][1]
    # Logo a seguir ao título de uma Parte não há quebra de página (a Parte não fica sozinha numa página)
    $brk = -not ($r -gt 0 -and $Plan[$r - 1][0] -ceq "H1B")
    switch -CaseSensitive ($t) {
        "H1B" { if ($brk) { Page-Break }; Emit-H 1 $a (Next-Id); $part = $a; $chap = "" }
        "H2" { $chap = $a; if (-not $Suppress.Contains($r)) { Emit-H 2 $a (Next-Id) } }
        "H2B" { if ($brk) { Page-Break }; $chap = $a; if (-not $Suppress.Contains($r)) { Emit-H 2 $a (Next-Id) } }
        "OWNER" { $B.Add(""); $B.Add("**Owner:** $a") }
        "FILE" {
            [void](Next-Id); $Sources.Add(@($a, $part, $chap))
            $B.Add(""); $B.Add("<a id=`"$($Fid[$a])`"></a>"); $B.Add(""); $B.Add("*Source: $a*"); $B.Add("")
            $script:Inf = $false; $doPromote = $Promote.Contains($a)
            foreach ($l in $FileLines[$a]) {
                if ((Test-Fence $l) -or $script:Inf) { $B.Add($l); continue }
                $lv = Get-HeadingLevel $l
                if ($lv -gt 0) { Emit-H $(if ($doPromote) { 2 } else { $lv + 2 }) (Rewrite-Line $script:HText $a) (Next-Id); $doPromote = $false }
                else { $B.Add((Rewrite-Line $l $a)) }
            }
        }
        "PM" {
            Emit-H 2 $PmName[$a] (Next-Id)
            $B.Add(""); $B.Add("*Source: [$a]($(Rel-Target $a))*"); $B.Add("")
            $B.Add("| Folder | Request | Method | URL |"); $B.Add("|---|---|---|---|")
            foreach ($row in $PmRows[$a]) { $B.Add($row) }
        }
        "SOURCES" {
            $B.Add(""); $B.Add("| # | Part | Chapter | Source file |"); $B.Add("|---|---|---|---|")
            $n = 0
            foreach ($s in $Sources) { $n++; $B.Add("| $n | $($s[1]) | $($s[2]) | [$($s[0])](#$($Fid[$s[0]])) |") }
        }
    }
}

# ---------------------------------------------------------------- capa, índice e corpo
$Doc = New-Object System.Collections.Generic.List[string]
$Doc.Add("# AISafe Flight Management System $EM Distributed Architecture (SIDIS 2026/2027, Assignment 1)")
$Doc.Add("")
$Doc.Add("**ISEP $EM Instituto Superior de Engenharia do Porto**")
$Doc.Add("")
$Doc.Add("**Generated:** " + (Get-Date -Format "yyyy-MM-dd"))
$Doc.Add("")
$Doc.Add("| Student | Number | Work package |")
$Doc.Add("|---|---|---|")
$Doc.Add("| Tiago Caetano | 1241945 | WP3 Flight Routes |")
$Doc.Add("| Tiago Moreira | 1240883 | WP1 Aircraft Management |")
$Doc.Add("| Afonso Ribeiro | 1240857 | WP2 Airports |")
$Doc.Add("| Diogo Fernandes | 1240632 | WP4 Maintenance |")
$Doc.Add("")
$Doc.Add('<div style="page-break-before: always;"></div>')
$Doc.Add("")
$Doc.Add("## Table of contents")
$Doc.Add("")
$Doc.AddRange($Toc)
$Doc.AddRange($B)

$outPath = Join-Path $Root $OutRel
[IO.File]::WriteAllText($outPath, ($Doc -join "`n") + "`n", $Utf8)
[Console]::Error.WriteLine("Generated $OutRel ($($Doc.Count) lines)")
