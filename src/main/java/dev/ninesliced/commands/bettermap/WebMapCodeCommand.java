package dev.ninesliced.commands.bettermap;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Root command collection for web map authentication codes.
 */
public class WebMapCodeCommand extends AbstractCommandCollection {

    public WebMapCodeCommand() {
        super("webmapcode", "Manage BetterMap web map login codes");
        addAliases("wmcode");
        addSubCommand(new WebMapCodeCreateCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected String generatePermissionNode() {
        return "";
    }
}
