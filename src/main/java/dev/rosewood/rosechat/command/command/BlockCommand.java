package dev.rosewood.rosechat.command.command;

import dev.rosewood.rosechat.RoseChat;
import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.command.argument.RoseChatArgumentHandlers;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import org.bukkit.Bukkit;

public class BlockCommand extends RoseChatCommand {

    public BlockCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("block")
                .descriptionKey("command-block-description")
                .permission("rosechat.block")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .required("player", RoseChatArgumentHandlers.OFFLINE_PLAYER)
                        .build())
                .build();
    }

    @Override
    protected boolean hasPriority() {
        return true;
    }

    @RoseExecutable
    public void execute(CommandContext context, String targetName) {
        Bukkit.getScheduler().runTaskAsynchronously(RoseChat.getInstance(), () -> {
            RosePlayer player = new RosePlayer(context.getSender());
            RosePlayer target = this.findPlayer(targetName);

            if (target == null) {
                player.sendLocaleMessage("invalid-argument",
                        StringPlaceholders.of("message",
                                this.getLocaleManager().getLocaleMessage("argument-handler-player")));
                return;
            }

            if (target.getUUID().equals(player.getUUID()) || target.hasPermission("rosechat.block.bypass")) {
                player.sendLocaleMessage("command-block-cannot-block");
                return;
            }

            PlayerData playerData = player.getPlayerData();
            if (playerData == null)
                return;

            if (playerData.isBlocking(target.getUUID())) {
                player.sendLocaleMessage("command-block-already-blocked",
                        StringPlaceholders.of("player", target.getName()));
                return;
            }

            this.getAPI().getPlayerData(target.getUUID(), targetData -> {
                playerData.block(target.getUUID());

                if (playerData.isFriend(target.getUUID()) || playerData.getFriendRequests().contains(target.getUUID())) {
                    playerData.removeFriend(target.getUUID());
                }

                boolean saveSelf = false;
                if (playerData.getReplyTo() != null && playerData.getReplyTo().equalsIgnoreCase(target.getRealName())) {
                    playerData.setReplyTo(null);
                    saveSelf = true;
                }
                if (saveSelf)
                    playerData.save();

                if (targetData != null) {
                    targetData.getFriends().remove(player.getUUID());
                    targetData.getFriendRequests().remove(player.getUUID());
                    if (targetData.getReplyTo() != null && targetData.getReplyTo().equalsIgnoreCase(player.getRealName())) {
                        targetData.setReplyTo(null);
                        targetData.save();
                    }
                }

                player.sendLocaleMessage("command-block-blocked",
                        StringPlaceholders.of("player", target.getName()));
            });
        });
    }

}
