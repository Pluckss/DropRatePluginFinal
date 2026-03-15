Add-Type -AssemblyName System.Drawing

function New-RoundedPath {
	param(
		[int]$X,
		[int]$Y,
		[int]$Width,
		[int]$Height,
		[int]$Radius
	)

	$path = New-Object System.Drawing.Drawing2D.GraphicsPath
	$diameter = $Radius * 2

	$path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
	$path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
	$path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
	$path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
	$path.CloseFigure()
	return $path
}

function Draw-ShadowedText {
	param(
		[System.Drawing.Graphics]$Graphics,
		[string]$Text,
		[System.Drawing.Font]$Font,
		[System.Drawing.Brush]$Brush,
		[System.Drawing.Brush]$ShadowBrush,
		[float]$X,
		[float]$Y
	)

	$Graphics.DrawString($Text, $Font, $ShadowBrush, $X + 2, $Y + 2)
	$Graphics.DrawString($Text, $Font, $Brush, $X, $Y)
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$assetsDir = Join-Path $repoRoot "assets"
if (-not (Test-Path $assetsDir)) {
	New-Item -ItemType Directory -Path $assetsDir | Out-Null
}

$outputPath = Join-Path $assetsDir "hero-example.png"

$width = 1600
$height = 900
$bitmap = New-Object System.Drawing.Bitmap($width, $height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

$backgroundRect = [System.Drawing.Rectangle]::new(0, 0, $width, $height)
$backgroundBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
	$backgroundRect,
	[System.Drawing.Color]::FromArgb(255, 16, 21, 28),
	[System.Drawing.Color]::FromArgb(255, 27, 39, 33),
	35
)
$graphics.FillRectangle($backgroundBrush, $backgroundRect)

$accentBrush1 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(38, 102, 187, 106))
$accentBrush2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(44, 255, 179, 71))
$graphics.FillEllipse($accentBrush1, -160, 520, 760, 420)
$graphics.FillEllipse($accentBrush2, 1020, -140, 520, 320)

$titleFont = New-Object System.Drawing.Font("Segoe UI Semibold", 34, [System.Drawing.FontStyle]::Bold)
$subtitleFont = New-Object System.Drawing.Font("Segoe UI", 17, [System.Drawing.FontStyle]::Regular)
$smallFont = New-Object System.Drawing.Font("Segoe UI", 13, [System.Drawing.FontStyle]::Regular)
$labelFont = New-Object System.Drawing.Font("Segoe UI Semibold", 14, [System.Drawing.FontStyle]::Bold)
$chatFont = New-Object System.Drawing.Font("Consolas", 22, [System.Drawing.FontStyle]::Regular)
$chatSmallFont = New-Object System.Drawing.Font("Segoe UI", 12, [System.Drawing.FontStyle]::Regular)

$whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(245, 245, 245))
$softWhiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(222, 230, 230, 230))
$mutedBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(188, 199, 204, 206))
$shadowBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(110, 0, 0, 0))
$greenBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(46, 125, 50))
$orangeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 140, 0))
$redBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(178, 34, 34))
$panelBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(210, 17, 20, 24))
$panelStroke = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(90, 255, 255, 255), 1.5)
$panelGlow = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(50, 255, 195, 85), 2)

Draw-ShadowedText $graphics "Drop Rate" $titleFont $whiteBrush $shadowBrush 112 112
Draw-ShadowedText $graphics "Instant drop odds in chat, right when loot hits the floor." $subtitleFont $softWhiteBrush $shadowBrush 116 176

$featureY = 270
$features = @(
	"Shows exact rates without leaving the game",
	"Uses color to separate common, uncommon, and rare drops",
	"Works best as a clean in-game loot feed"
)

foreach ($feature in $features) {
	$graphics.FillEllipse($softWhiteBrush, 120, $featureY + 8, 10, 10)
	$graphics.DrawString($feature, $smallFont, $mutedBrush, 148, $featureY)
	$featureY += 52
}

$legendY = 492
$graphics.DrawString("Color legend", $labelFont, $softWhiteBrush, 116, $legendY)

$graphics.FillEllipse($greenBrush, 120, $legendY + 44, 18, 18)
$graphics.DrawString("Common", $smallFont, $mutedBrush, 152, $legendY + 36)

$graphics.FillEllipse($orangeBrush, 120, $legendY + 92, 18, 18)
$graphics.DrawString("Uncommon", $smallFont, $mutedBrush, 152, $legendY + 84)

$graphics.FillEllipse($redBrush, 120, $legendY + 140, 18, 18)
$graphics.DrawString("Rare", $smallFont, $mutedBrush, 152, $legendY + 132)

$panelX = 840
$panelY = 116
$panelWidth = 620
$panelHeight = 620
$panelPath = New-RoundedPath -X $panelX -Y $panelY -Width $panelWidth -Height $panelHeight -Radius 28
$graphics.FillPath($panelBrush, $panelPath)
$graphics.DrawPath($panelStroke, $panelPath)
$graphics.DrawPath($panelGlow, $panelPath)

$headerRect = [System.Drawing.Rectangle]::new($panelX + 26, $panelY + 24, $panelWidth - 52, 72)
$headerPath = New-RoundedPath -X $headerRect.X -Y $headerRect.Y -Width $headerRect.Width -Height $headerRect.Height -Radius 18
$headerBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(88, 255, 255, 255))
$graphics.FillPath($headerBrush, $headerPath)
$graphics.DrawString("In-game chat example", $labelFont, $whiteBrush, $panelX + 48, $panelY + 46)
$graphics.DrawString("What players understand in 2 seconds", $chatSmallFont, $softWhiteBrush, $panelX + 48, $panelY + 72)

$lines = @(
	@("1x Rune sword (6/378)", $greenBrush),
	@("1x Abyssal whip (1/512)", $orangeBrush),
	@("1x Jar of venom (1/1500)", $redBrush)
)

$lineY = $panelY + 176
foreach ($line in $lines) {
	$text = [string]$line[0]
	$brush = [System.Drawing.Brush]$line[1]

	Draw-ShadowedText $graphics $text $chatFont $brush $shadowBrush ($panelX + 42) $lineY
	$lineY += 100
}

$tipRect = [System.Drawing.Rectangle]::new($panelX + 32, $panelY + 494, $panelWidth - 64, 92)
$tipPath = New-RoundedPath -X $tipRect.X -Y $tipRect.Y -Width $tipRect.Width -Height $tipRect.Height -Radius 18
$tipBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(56, 10, 12, 15))
$graphics.FillPath($tipBrush, $tipPath)
$graphics.DrawString("Green = common  Orange = uncommon  Red = rare", $labelFont, $softWhiteBrush, $panelX + 56, $panelY + 520)
$graphics.DrawString("Color is based on effective chance, including bundle drops like 6/378 or 12/378.", $chatSmallFont, $mutedBrush, $panelX + 56, $panelY + 552)

$bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)

$backgroundBrush.Dispose()
$accentBrush1.Dispose()
$accentBrush2.Dispose()
$titleFont.Dispose()
$subtitleFont.Dispose()
$smallFont.Dispose()
$labelFont.Dispose()
$chatFont.Dispose()
$chatSmallFont.Dispose()
$whiteBrush.Dispose()
$softWhiteBrush.Dispose()
$mutedBrush.Dispose()
$shadowBrush.Dispose()
$greenBrush.Dispose()
$orangeBrush.Dispose()
$redBrush.Dispose()
$panelBrush.Dispose()
$panelStroke.Dispose()
$panelGlow.Dispose()
$headerBrush.Dispose()
$tipBrush.Dispose()
$graphics.Dispose()
$bitmap.Dispose()

Write-Output $outputPath
