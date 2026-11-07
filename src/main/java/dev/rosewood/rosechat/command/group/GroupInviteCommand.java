package dev.rosewood.rosechat.command.group;

import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.command.argument.RoseChatArgumentHandlers;
import dev.rosewood.rosechat.hook.channel.rosechat.GroupChannel;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class GroupInviteCommand extends RoseChatCommand {

    public GroupInviteCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("invite")
                .descriptionKey("command-gc-invite-description")
                .permission("rosechat.group.invite")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .required("player", RoseChatArgumentHandlers.ROSE_PLAYER)
                        .optional("group", RoseChatArgumentHandlers.MEMBER_GROUP)
                        .build())
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context, RosePlayer target) {
        RosePlayer player = new RosePlayer(context.getSender());
        GroupChannel group = player.getOwnedGroupChannel();
        if (group == null) {
            player.sendLocaleMessage("no-gc");
            return;
        }
        this.inviteToGroup(player, target, group);
    }

    @RoseExecutable
    public void execute(CommandContext context, RosePlayer target, GroupChannel group) {
        RosePlayer player = new RosePlayer(context.getSender());
        if (!group.getOwner().equals(player.getUUID()) && !player.hasPermission("rosechat.group.admin")) {
            player.sendLocaleMessage("no-gc");
            return;
        }
        this.inviteToGroup(player, target, group);
    }

    private void inviteToGroup(RosePlayer player, RosePlayer target, GroupChannel group) {
        if (group.getMembers().contains(target.getUUID())) {
            player.sendLocaleMessage("command-gc-invite-member");
            return;
        }

        target.invite(group);

        player.sendLocaleMessage("command-gc-invite-success",
                StringPlaceholders.of(
                        "player", target.getName(),
                        "name", group.getName()
                ));
        target.sendLocaleMessage("command-gc-invite-invited",
                StringPlaceholders.of(
                        "player", player.getName(),
                        "name", group.getName()
                ));

        // Build and send the clickable accept/deny buttons with full hex color support.
        BaseComponent[] acceptComponents = TextComponent.fromLegacyText(
                this.getLocaleManager().getLocaleMessage("command-gc-accept-accept"));
        HoverEvent acceptHover = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(TextComponent.fromLegacyText(this.getLocaleManager().getLocaleMessage("command-gc-accept-hover"))));
        ClickEvent acceptClick = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/group accept " + group.getId());
        for (BaseComponent comp : acceptComponents) {
            comp.setHoverEvent(acceptHover);
            comp.setClickEvent(acceptClick);
        }

        BaseComponent[] denyComponents = TextComponent.fromLegacyText(
                this.getLocaleManager().getLocaleMessage("command-gc-deny-deny"));
        HoverEvent denyHover = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(TextComponent.fromLegacyText(this.getLocaleManager().getLocaleMessage("command-gc-deny-hover"))));
        ClickEvent denyClick = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/group deny " + group.getId());
        for (BaseComponent comp : denyComponents) {
            comp.setHoverEvent(denyHover);
            comp.setClickEvent(denyClick);
        }

        ComponentBuilder builder = new ComponentBuilder();
        builder.append("          ");
        builder.append(acceptComponents, ComponentBuilder.FormatRetention.NONE);
        builder.append("          ", ComponentBuilder.FormatRetention.NONE);
        builder.append(denyComponents, ComponentBuilder.FormatRetention.NONE);

        target.send(builder.create());
    }

}
