package dev.rosewood.rosechat.command.command;

import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import java.util.List;
import java.util.UUID;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;

public class BlocklistCommand extends RoseChatCommand {

    public BlocklistCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("blocklist")
                .aliases("blocked")
                .permission("rosechat.block")
                .descriptionKey("command-blocklist-description")
                .playerOnly(true)
                .build();
    }

    @Override
    protected boolean hasPriority() {
        return true;
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        RosePlayer player = new RosePlayer(context.getSender());
        PlayerData data = player.getPlayerData();
        if (data == null)
            return;

        List<UUID> blocked = data.getBlockedPlayers();
        player.sendLocaleMessage("command-blocklist-title",
                StringPlaceholders.of("amount", blocked.size()));

        if (blocked.isEmpty())
            return;

        String unblockLabel = this.getLocaleManager().getMessage("command-blocklist-unblock-button");
        String unblockHover = this.getLocaleManager().getMessage("command-blocklist-unblock-hover");

        for (UUID uuid : blocked) {
            String displayName = MessageUtils.resolvePlayerName(uuid);
            String rawName = Bukkit.getOfflinePlayer(uuid).getName();
            String commandTarget = rawName != null ? rawName : displayName;

            ComponentBuilder builder = new ComponentBuilder();
            builder.append(MessageUtils.clickableButton(
                    player,
                    "<#8E99A2> - <#FFCCCC>" + displayName + " ",
                    null, null, null));
            builder.append(MessageUtils.clickableButton(
                    player,
                    unblockLabel,
                    unblockHover.replace("%player%", displayName),
                    ClickEvent.Action.RUN_COMMAND,
                    "/unblock " + commandTarget), ComponentBuilder.FormatRetention.NONE);

            player.send(builder.create());
        }
    }

}
