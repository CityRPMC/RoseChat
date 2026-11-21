package dev.rosewood.rosechat.command.command;

import dev.rosewood.rosechat.RoseChat;
import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.Argument;
import dev.rosewood.rosegarden.command.framework.ArgumentHandler;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.InputIterator;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class UnblockCommand extends RoseChatCommand {

    public UnblockCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("unblock")
                .descriptionKey("command-unblock-description")
                .permission("rosechat.block")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .required("player", new ArgumentHandler<String>(String.class) {
                            @Override
                            public String handle(CommandContext context, Argument argument, InputIterator inputIterator) {
                                return inputIterator.next();
                            }

                            @Override
                            public List<String> suggest(CommandContext context, Argument argument, String[] args) {
                                if (!(context.getSender() instanceof Player sender))
                                    return Collections.emptyList();
                                PlayerData data = RoseChatAPI.getInstance().getPlayerData(sender.getUniqueId());
                                if (data == null)
                                    return Collections.emptyList();
                                return data.getBlockedPlayers().stream()
                                        .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                                        .filter(Objects::nonNull)
                                        .toList();
                            }
                        })
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
            PlayerData playerData = player.getPlayerData();
            if (playerData == null)
                return;

            RosePlayer target = this.findPlayer(targetName);
            if (target == null) {
                for (var blockedUuid : playerData.getBlockedPlayers()) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(blockedUuid);
                    if (offline.getName() != null && offline.getName().equalsIgnoreCase(targetName)) {
                        target = new RosePlayer(offline);
                        break;
                    }
                }
            }

            if (target == null) {
                player.sendLocaleMessage("invalid-argument",
                        StringPlaceholders.of("message",
                                this.getLocaleManager().getLocaleMessage("argument-handler-player")));
                return;
            }

            if (!playerData.isBlocking(target.getUUID())) {
                player.sendLocaleMessage("command-unblock-not-blocked",
                        StringPlaceholders.of("player", target.getName()));
                return;
            }

            playerData.unblock(target.getUUID());
            player.sendLocaleMessage("command-unblock-unblocked",
                    StringPlaceholders.of("player", target.getName()));
        });
    }

}
