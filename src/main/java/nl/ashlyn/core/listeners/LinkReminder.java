package nl.ashlyn.core.listeners;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.PlayerLinks;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Created by Ashlyn on 15/11/2026
 *
 * Tells players who join that their account isn't linked to Watchdog yet, with a link to the portal.
 */
public class LinkReminder implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!CoreConfig.isPortalEnabled()) return;
        Player player = event.getPlayer();

        Core.getInstance().getServer().getScheduler().runTaskLater(Core.getInstance(), () -> {
            if (!player.isOnline() || PlayerLinks.find(player.getUniqueId()) != null) return;
            String url = CoreConfig.getPortalUrl();

            TextComponent link = new TextComponent(url);
            link.setColor(ChatColor.WHITE.asBungee());
            link.setUnderlined(true);
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new ComponentBuilder("Open het portaal").create())));

            TextComponent message = new TextComponent(TextComponent.fromLegacy(ChatColor.LIGHT_PURPLE + "Je account is nog niet gekoppeld aan Watchdog."
                    + ChatColor.GRAY + " Log in op het portaal voor een code en gebruik /koppel <code>: "));
            message.addExtra(link);
            player.spigot().sendMessage(message);
        }, 60L);
    }
}
