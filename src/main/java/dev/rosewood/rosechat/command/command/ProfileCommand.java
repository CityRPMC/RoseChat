package dev.rosewood.rosechat.command.command;

import dev.rosewood.rosechat.RoseChat;
import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.command.argument.RoseChatArgumentHandlers;
import dev.rosewood.rosechat.hook.DigiDocumentsHook;
import dev.rosewood.rosechat.hook.DigiDocumentsHook.CivilDocument;
import dev.rosewood.rosechat.hook.channel.rosechat.GroupChannel;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;

public class ProfileCommand extends RoseChatCommand {

    public ProfileCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("profile")
                .aliases("social")
                .descriptionKey("command-profile-description")
                .permission("rosechat.profile")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .optional("player", RoseChatArgumentHandlers.OFFLINE_PLAYER)
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
        this.showProfile(player, player);
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

            this.showProfile(player, target);
        });
    }

    private void showProfile(RosePlayer viewer, RosePlayer target) {
        this.getAPI().getPlayerData(target.getUUID(), targetData ->
                DigiDocumentsHook.fetchDocument(target.getUUID()).thenAccept(docOpt ->
                        Bukkit.getScheduler().runTask(RoseChat.getInstance(), () ->
                                this.renderProfile(viewer, target, targetData, docOpt))));
    }

    private void renderProfile(RosePlayer viewer, RosePlayer target, PlayerData targetData, Optional<CivilDocument> docOpt) {
        if (!viewer.isPlayer())
            return;

        PlayerData viewerData = viewer.getPlayerData();
        boolean isSelf = viewer.getUUID().equals(target.getUUID());
        boolean isOnline = target.isPlayer() && !MessageUtils.isPlayerVanished(target.asPlayer());

        this.getLocaleManager().sendComponentMessage(viewer.asPlayer(), "command-profile-header",
                StringPlaceholders.of("player", target.getName()), false);

        String onlineText = isOnline
                ? this.getLocaleManager().getMessage("command-profile-online")
                : this.getLocaleManager().getMessage("command-profile-offline");
        if (targetData != null && targetData.isDnd()) {
            onlineText += " " + this.getLocaleManager().getMessage("command-profile-dnd-tag");
        }
        this.getLocaleManager().sendComponentMessage(viewer.asPlayer(), "command-profile-presence",
                StringPlaceholders.of("presence", onlineText), false);

        if (docOpt.isPresent()) {
            CivilDocument doc = docOpt.get();
            this.getLocaleManager().sendComponentMessage(viewer.asPlayer(), "command-profile-rp-identity",
                    StringPlaceholders.of(
                            "rp_name", doc.fullName(),
                            "rp_age", doc.age(),
                            "rp_pronouns", doc.formattedPronouns()
                    ), false);
        } else {
            this.getLocaleManager().sendComponentMessage(viewer.asPlayer(), "command-profile-rp-unregistered", false);
        }

        String status = (targetData != null && targetData.getStatus() != null)
                ? targetData.getStatus()
                : this.getLocaleManager().getMessage("command-profile-status-none");
        this.getLocaleManager().sendComponentMessage(viewer.asPlayer(), "command-profile-status",
                StringPlaceholders.of("status", status), false);

        if (!isSelf) {
            List<GroupChannel> viewerGroups = this.getAPI().getGroupChats(viewer.getUUID());
            String mutualGroups = viewerGroups.stream()
                    .filter(gc -> gc.getMembers().contains(target.getUUID()))
                    .map(GroupChannel::getName)
                    .collect(Collectors.joining(", "));
            if (!mutualGroups.isEmpty()) {
                this.getLocaleManager().sendComponentMessage(viewer.asPlayer(), "command-profile-mutual-groups",
                        StringPlaceholders.of("groups", mutualGroups), false);
            }

            if (viewerData != null) {
                ComponentBuilder actions = new ComponentBuilder();
                String realName = target.getRealName();
                boolean blockedByViewer = viewerData.isBlocking(target.getUUID());
                boolean mutualBlocked = this.getAPI().isBlocked(viewer.getUUID(), target.getUUID());

                if (blockedByViewer) {
                    actions.append(MessageUtils.clickableButton(
                            viewer,
                            this.getLocaleManager().getMessage("command-profile-btn-unblock"),
                            "<#CCFFCC>Click to unblock " + target.getName(),
                            ClickEvent.Action.RUN_COMMAND,
                            "/unblock " + realName));
                } else {
                    if (!mutualBlocked && isOnline) {
                        actions.append(MessageUtils.clickableButton(
                                viewer,
                                this.getLocaleManager().getMessage("command-profile-btn-message"),
                                "<#CCCCFF>Click to message " + target.getName(),
                                ClickEvent.Action.SUGGEST_COMMAND,
                                "/msg " + realName + " "));
                        actions.append(" ", ComponentBuilder.FormatRetention.NONE);

                        if (viewerData.isFriend(target.getUUID())) {
                            actions.append(MessageUtils.clickableButton(
                                    viewer,
                                    this.getLocaleManager().getMessage("command-profile-btn-unfriend"),
                                    "<#FFE6CC>Click to remove friend",
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/friend remove " + realName), ComponentBuilder.FormatRetention.NONE);
                        } else {
                            actions.append(MessageUtils.clickableButton(
                                    viewer,
                                    this.getLocaleManager().getMessage("command-profile-btn-add-friend"),
                                    "<#CCFFCC>Click to send friend request",
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/friend add " + realName), ComponentBuilder.FormatRetention.NONE);
                        }
                        actions.append(" ", ComponentBuilder.FormatRetention.NONE);

                        GroupChannel ownedGroup = viewer.getOwnedGroupChannel();
                        if (ownedGroup != null && !ownedGroup.getMembers().contains(target.getUUID())) {
                            actions.append(MessageUtils.clickableButton(
                                    viewer,
                                    this.getLocaleManager().getMessage("command-profile-btn-invite-gc"),
                                    "<#FFAA00>Click to invite to " + ownedGroup.getName(),
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/gc invite " + realName), ComponentBuilder.FormatRetention.NONE);
                            actions.append(" ", ComponentBuilder.FormatRetention.NONE);
                        }
                    } else if (viewerData.isFriend(target.getUUID())) {
                        actions.append(MessageUtils.clickableButton(
                                viewer,
                                this.getLocaleManager().getMessage("command-profile-btn-unfriend"),
                                "<#FFE6CC>Click to remove friend",
                                ClickEvent.Action.RUN_COMMAND,
                                "/friend remove " + realName), ComponentBuilder.FormatRetention.NONE);
                        actions.append(" ", ComponentBuilder.FormatRetention.NONE);
                    }

                    boolean isIgnored = viewerData.getIgnoringPlayers().contains(target.getUUID());
                    actions.append(MessageUtils.clickableButton(
                            viewer,
                            this.getLocaleManager().getMessage(isIgnored ? "command-profile-btn-unignore" : "command-profile-btn-ignore"),
                            isIgnored ? "<#CCFFCC>Click to unignore" : "<#FFE6CC>Click to ignore",
                            ClickEvent.Action.RUN_COMMAND,
                            "/ignore " + realName), ComponentBuilder.FormatRetention.NONE);
                    actions.append(" ", ComponentBuilder.FormatRetention.NONE);

                    actions.append(MessageUtils.clickableButton(
                            viewer,
                            this.getLocaleManager().getMessage("command-profile-btn-block"),
                            "<#FFCCCC>Click to block " + target.getName(),
                            ClickEvent.Action.RUN_COMMAND,
                            "/block " + realName), ComponentBuilder.FormatRetention.NONE);
                }

                viewer.send(actions.create());
            }
        }

        this.getLocaleManager().sendComponentMessage(viewer.asPlayer(), "command-profile-footer", false);
    }

}
