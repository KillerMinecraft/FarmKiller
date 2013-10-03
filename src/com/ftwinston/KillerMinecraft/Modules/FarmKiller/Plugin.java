package com.ftwinston.KillerMinecraft.Modules.FarmKiller;

import org.bukkit.Material;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.GameModePlugin;

public class Plugin extends GameModePlugin
{
	@Override
	public Material getMenuIcon() { return Material.WHEAT; }
	
	@Override
	public String[] getDescriptionText() { return new String[] {"Teams compete to deliver as much", "farm produce as possible to a", "drop-off point. Plenty of", "opportunities for sabotage."}; }
	
	@Override
	public GameMode createInstance()
	{
		return new FarmKiller();
	}
}