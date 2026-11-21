package dev.rosewood.rosechat.command.command;

import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.config.Settings;
import dev.rosewood.rosechat.message.MessageRules;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.PermissionArea;
import dev.rosewood.rosechat.message.RoseMessage;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.argument.ArgumentHandlers;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.HexUtils;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class StatusCommand extends RoseChatCommand {

    public StatusCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("status")
                .descriptionKey("command-status-description")
                .permission("rosechat.status")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .optional("text", ArgumentHandlers.GREEDY_STRING)
                        .build())
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

        if (data.getStatus() == null) {
            player.sendLocaleMessage("command-status-none");
        } else {
            player.sendLocaleMessage("command-status-current",
                    StringPlaceholders.of("status", data.getStatus()));
        }
    }

    @RoseExecutable
    public void execute(CommandContext context, String text) {
        RosePlayer player = new RosePlayer(context.getSender());
        PlayerData data = player.getPlayerData();
        if (data == null)
            return;

        if (text.equalsIgnoreCase("clear") || text.equalsIgnoreCase("off")
                || text.equalsIgnoreCase("remove") || text.equalsIgnoreCase("reset")) {
            data.setStatus(null);
            data.save();
            player.sendLocaleMessage("command-status-cleared");
            return;
        }

        if (!MessageUtils.canColor(player, text, PermissionArea.MESSAGE)) {
            player.sendLocaleMessage("no-permission");
            return;
        }

        String stripped = ChatColor.stripColor(HexUtils.colorify(text));
        int maxLen = Settings.MAX_STATUS_LENGTH.get();
        if (stripped != null && stripped.length() > maxLen) {
            player.sendLocaleMessage("command-status-too-long",
                    StringPlaceholders.of("max", maxLen));
            return;
        }

        RoseMessage roseMessage = RoseMessage.forLocation(player, PermissionArea.MESSAGE);
        MessageRules rules = new MessageRules().applyAllFilters().ignoreMessageLogging();
        MessageRules.RuleOutputs outputs = rules.apply(roseMessage, text);
        if (outputs.isBlocked()) {
            if (outputs.getWarningMessage() != null) {
                player.send(outputs.getWarningMessage());
            } else if (outputs.getWarning() != null) {
                outputs.getWarning().send(player);
            }

            if (Settings.SEND_BLOCKED_MESSAGES_TO_STAFF.get() && outputs.shouldNotifyStaff()) {
                for (Player staffPlayer : Bukkit.getOnlinePlayers()) {
                    if (staffPlayer.hasPermission("rosechat.seeblocked")) {
                        new RosePlayer(staffPlayer).sendLocaleMessage("blocked-message",
                                StringPlaceholders.of("player", player.getName(), "message", text));
                    }
                }
            }
            return;
        }

        String finalStatus = outputs.getFilteredMessage();
        data.setStatus(finalStatus);
        data.save();

        player.sendLocaleMessage("command-status-set",
                StringPlaceholders.of("status", finalStatus));
    }

}
