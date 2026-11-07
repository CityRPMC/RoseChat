package dev.rosewood.rosechat.command.command;

import dev.rosewood.rosechat.RoseChat;
import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.config.Settings;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.argument.ArgumentHandlers;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import org.bukkit.Bukkit;

public class ReplyCommand extends RoseChatCommand {

    public ReplyCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("r")
                .aliases("reply")
                .descriptionKey("command-reply-description")
                .permission("rosechat.reply")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .optional("message", ArgumentHandlers.GREEDY_STRING)
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
        String targetName = player.getPlayerData().getReplyTo();
        if (targetName == null) {
            player.sendLocaleMessage("command-reply-no-one");
            return;
        }

        RosePlayer target = this.findPlayer(targetName);
        if (target != null && target.isPlayer()) {
            if (MessageUtils.isPlayerVanished(target.asPlayer()) && !Settings.ALLOW_MESSAGING_VANISHED_PLAYERS.get()
                    && !player.hasPermission("rosechat.messagebypass")) {
                player.sendLocaleMessage("command-reply-no-one");
                return;
            }
            player.sendLocaleMessage("command-reply-current",
                    StringPlaceholders.of("player", target.getName()));
            return;
        }

        if (this.getAPI().isBungee() && this.getAPI().getBungeeManager().getAllPlayers().contains(targetName)) {
            player.sendLocaleMessage("command-reply-current",
                    StringPlaceholders.of("player", targetName));
            return;
        }

        player.sendLocaleMessage("command-reply-no-one");
    }

    @RoseExecutable
    public void execute(CommandContext context, String message) {
        Bukkit.getScheduler().runTaskAsynchronously(RoseChat.getInstance(), () -> {
            RosePlayer player = new RosePlayer(context.getSender());
            String targetName = player.getPlayerData().getReplyTo();
            if (targetName == null) {
                player.sendLocaleMessage("command-reply-no-one");
                return;
            }

            if (MessageUtils.isMessageEmpty(message)) {
                player.sendLocaleMessage("message-blank");
                return;
            }

            RosePlayer target = this.findPlayer(targetName);
            if (target != null && target.isPlayer()) {
                if (MessageUtils.isPlayerVanished(target.asPlayer()) && !Settings.ALLOW_MESSAGING_VANISHED_PLAYERS.get()
                        && !player.hasPermission("rosechat.messagebypass")) {
                    player.sendLocaleMessage("command-message-vanished-player");
                    return;
                }
            }

            if (target != null && !context.getSender().hasPermission("rosechat.togglemessage.bypass")) {
                PlayerData targetData = target.getPlayerData();
                if (targetData != null && !targetData.canBeMessaged()) {
                    player.sendLocaleMessage("command-togglemessage-cannot-message");
                    return;
                }
            }

            MessageUtils.sendPrivateMessage(player, targetName, message);

            if (this.getAPI().isBungee())
                this.getAPI().getBungeeManager().sendUpdateReply(player.getRealName(), targetName);

            if (target != null && target.isPlayer()) {
                PlayerData targetData = target.getPlayerData();
                if (targetData != null) {
                    targetData.setReplyTo(player.getRealName());
                    targetData.save();
                }
            }
        });
    }

}
