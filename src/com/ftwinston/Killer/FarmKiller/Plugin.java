package com.ftwinston.Killer.FarmKiller;

import com.ftwinston.Killer.GameMode;
import com.ftwinston.Killer.GameModePlugin;
import com.ftwinston.Killer.Killer;

public class Plugin extends GameModePlugin
{
	public void onEnable()
	{
		Killer.registerGameMode(this);
	}
	
	@Override
	public GameMode createInstance()
	{
		return new FarmKiller();
	}

	@Override
	public String[] getSignDescription()
	{
		return new String[] {
			"Players are put",
			"into teams.",
			"They compete to",
			"produce crops.",
			
			"Crops & animals",
			"need delivered",
			"to a central",
			"drop-off point.",
			
			"You respawn,",
			"so sabotaging",
			"your opponents",
			"is recommended!"
		};
	}
}