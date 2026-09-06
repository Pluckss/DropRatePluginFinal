package com.pluckss.droprate;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Notices which item the player is hovering on the clue scroll reward screen, so the
 * same drop-rate tooltip the Collection Log gets can be shown there too.
 * <p>
 * The Collection Log hook reads the hovered item straight off a {@code MenuEntry},
 * which is not an option here: the reward screen is a display-only interface
 * (group {@link InterfaceID#TRAIL_REWARDSCREEN}, five children, no per-item menu
 * options), so its item slots raise no menu entries to read. {@link WidgetItemOverlay}
 * is RuneLite's supported way in — it is handed the real canvas rectangle of every
 * item the client draws on that interface, which is enough to hit-test the cursor.
 * <p>
 * Drawing is still left to {@link ClogTooltipOverlay}; this class only records what
 * is under the mouse.
 */
class ClueRewardTooltipOverlay extends WidgetItemOverlay
{
	private final Client client;
	private final DropRatePlugin plugin;

	@Inject
	ClueRewardTooltipOverlay(Client client, DropRatePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		showOnInterfaces(InterfaceID.TRAIL_REWARDSCREEN);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!plugin.isClueRewardTooltipEnabled())
		{
			return;
		}

		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds == null || !bounds.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}

		plugin.setHoveredRewardItem(itemId);
	}
}
