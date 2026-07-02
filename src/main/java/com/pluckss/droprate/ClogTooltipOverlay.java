package com.pluckss.droprate;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Renders the drop-rate tooltip while hovering an item in the Collection Log.
 * Draws nothing itself — it delegates to the shared {@link TooltipManager}, which
 * anchors the boxed tooltip to the cursor. The plugin decides (per frame) whether
 * there is anything to show via {@link DropRatePlugin#buildActiveClogTooltip()}.
 */
class ClogTooltipOverlay extends Overlay
{
	private final DropRatePlugin plugin;
	private final TooltipManager tooltipManager;

	@Inject
	ClogTooltipOverlay(DropRatePlugin plugin, TooltipManager tooltipManager)
	{
		this.plugin = plugin;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.TOOLTIP);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String text = plugin.buildActiveClogTooltip();
		if (text != null)
		{
			tooltipManager.add(new Tooltip(text));
		}

		return null;
	}
}
