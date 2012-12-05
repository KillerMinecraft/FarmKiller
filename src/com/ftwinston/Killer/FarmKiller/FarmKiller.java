package com.ftwinston.Killer.FarmKiller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ftwinston.Killer.GameMode;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Material;

public class FarmKiller extends GameMode
{
	public static final int friendlyFire = 0, diminishingReturns = 1, optionTwoTeams = 2, optionThreeTeams = 3, optionFourTeams = 4, optionTwoDays = 5, optionFourDays = 6, optionSixDays = 8, optionEightDays = 9;

	private int[] teamScores;
	private int dayCountProcessID, dayCount = 0, dayLimit = 4;
	private int numTeams = 2;
	
	@Override
	public int getMinPlayers() { return numTeams; } // one player on each team is our minimum
	
	@Override
	public Option[] setupOptions()
	{
		Option[] options = {
			new Option("Players can hurt teammates", true),
			new Option("Diminishing returns on each item type", true),
			new Option("Two teams", true),
			new Option("Three teams", false),
			new Option("Four teams", false),
			new Option("Game lasts for two days", true),
			new Option("Game lasts for four days", false),
			new Option("Game lasts for six days", false),
			new Option("Game lasts for eight days", false)
		};
		
		return options;
	}
	
	@Override
	public String getHelpMessage(int num, int team)
	{
		switch ( num )
		{
			case 0:
			{
				String numText;
				switch ( numTeams )
				{
					case 2:
						numText = "two "; break;
					case 3:
						numText = "three "; break;
					case 4:
						numText = "four "; break;
					default:
						numText = Integer.toString(numTeams); break;
				}
				return "Players have been split into " + numText + "teams. Get farming!\nThe scoreboard shows what team each player is on.";
			}
			case 1:
				return "The teams complete to deliver the most farm produce (plants, meat, eggs, wool and leather - no seeds) to a central depot.";
			
			case 2:
				return "At the end of " + dayLimit + " days, the team that has the highest score wins the game.";
				
			default:
				return null;
		}
	}
	
	@Override
	public boolean teamAllocationIsSecret() { return false; }
	
	@Override
	public Environment[] getWorldsToGenerate() { return new Environment[] { Environment.NORMAL }; }
	
	private Location dropOffCenter;
	
	@Override
	public void worldGenerationComplete()
	{
		World main = getWorld(0);
		dropOffCenter = new Location(main, 0, main.getSeaLevel()+10, 0);
		
		// create a grassy plain around where the drop off will be
		createFlatSurface();
		
		// generate the central drop-off point itself
		createDropOff();
	
		// generate spawn points for each team
		for ( int team=0; team<numTeams; team++ )
		{
			Location spawn = getSpawnLocationForTeam(team);
			for ( int x = spawn.getBlockX()-2; x<=spawn.getBlockX()+2; x++ )
				for ( int z = spawn.getBlockZ()-2; z<=spawn.getBlockZ()+2; z++ )
				{
					Block b = main.getBlockAt(x, spawn.getBlockY()-1, z); 
					b.setType(Material.WOOL);
					b.setData(getTeamWoolColor(team));
				}
			spawn.getBlock().getRelative(BlockFace.DOWN).setType(Material.BEDROCK);
		}
	}
	
	@Override
	public boolean isLocationProtected(Location l)
	{
		int cy = dropOffCenter.getBlockY(), ly = l.getBlockY();
		if ( l.getBlockY() < cy - 1 || ly > cy + 5 )
			return false;
		
		// the drop-off building is protected
		int cx =  dropOffCenter.getBlockX(), cz = dropOffCenter.getBlockZ(), lx = l.getBlockX(), lz = l.getBlockZ();
		if ( lx > cx - 4 && lx < cx + 4
		  && lz > cz - 4 && lz < cz + 4 )
			return true;
			
		// the spawn point for each team is also protected 
		for ( int team=0; team<numTeams; team++ )
		{
			Location spawn = getSpawnLocationForTeam(team);
			if ( lx == spawn.getBlockX() && lz == spawn.getBlockZ() && ly < cy + 2 )
				return true;
		}
		return false;
	}
	
	public void createFlatSurface()
	{
		final int range = 45, fadeLength = 16;
		
		int dropOffX = dropOffCenter.getBlockX(), dropOffY = dropOffCenter.getBlockY(), dropOffZ = dropOffCenter.getBlockZ();
		World world = dropOffCenter.getWorld();
		
		for ( int x=dropOffX-range; x<=dropOffX+range; x++ )
			for ( int z=dropOffZ-range; z<=dropOffZ+range; z++ )
				fillInAboveBelow(world, x, dropOffY-1, z);
		
		int minZ = dropOffZ-range, maxZ = dropOffZ+range, minX = dropOffX-range, maxX = dropOffX+range;
		for ( int fade = 1; fade<=fadeLength; fade++ )
		{
			float fraction = ((float)fade)/fadeLength;
			for ( int z=minZ-fade; z<=maxZ+fade; z++ )
			{
				int x = dropOffX+range+fade;
				int y = (int)(0.5f + getHighestGroundYAt(world, x, z) * fraction + (dropOffY-1) * (1f - fraction));
				fillInAboveBelow(world, x, y, z);
				
				x = dropOffX-range-fade;
				y = (int)(0.5f + getHighestGroundYAt(world, x, z) * fraction + (dropOffY-1) * (1f - fraction));
				fillInAboveBelow(world, x, y, z);
			}
			
			for ( int x=minX-fade+1; x<maxX+fade; x++ )
			{
				int z = dropOffZ+range+fade;
				int y = (int)(0.5f + getHighestGroundYAt(world, x, z) * fraction + (dropOffY-1) * (1f - fraction));
				fillInAboveBelow(world, x, y, z);
				
				z = dropOffZ-range-fade;
				y = (int)(0.5f + getHighestGroundYAt(world, x, z) * fraction + (dropOffY-1) * (1f - fraction));
				fillInAboveBelow(world, x, y, z);
			}
		}
	}
	
	private int getHighestGroundYAt(World world, int x, int z)
	{
		int y = world.getHighestBlockYAt(x,  z);
		Block b = world.getBlockAt(x, y, z);
		
		while ( y > world.getSeaLevel() )
		{
			if ( b.getType() == Material.GRASS || b.getType() == Material.DIRT || b.getType() == Material.STONE )
				break;

			y--;
			b = world.getBlockAt(x, y, z);
		}

		return y;
	}
	
	private void fillInAboveBelow(World world, int x, int groundY, int z)
	{
		int y = groundY-1, maxY = world.getMaxHeight();
		Block b = world.getBlockAt(x, y, z);
		do
		{
			b.setType(Material.DIRT);
			
			y--;
			b = world.getBlockAt(x, y, z);
		}
		while ( b.getType() != Material.DIRT && b.getType() != Material.STONE && b.getType() != Material.BEDROCK );
		
		int prevMaxY = world.getHighestBlockAt(x, z).getY();
		for ( y=prevMaxY; y<groundY; y++ )
			world.getBlockAt(x,y,z).setType(Material.DIRT);
		
		world.getBlockAt(x,groundY,z).setType(Material.GRASS);
		
		for ( y=groundY+1; y<maxY; y++ )
			world.getBlockAt(x,y,z).setType(Material.AIR);
	}
	
	public void createDropOff()
	{
		int xmin = dropOffCenter.getBlockX() - 3, xmax = dropOffCenter.getBlockX() + 3;
		int zmin = dropOffCenter.getBlockZ() - 3, zmax = dropOffCenter.getBlockZ() + 3;
		int ymin = dropOffCenter.getBlockY();

		World w = dropOffCenter.getWorld();
		
		// now, generate a hut for the drop-off
		for ( int x=xmin+1; x < xmax; x++ )
		{
			w.getBlockAt(x, ymin, zmin + 1).setType(Material.WOOD);
			w.getBlockAt(x, ymin, zmax - 1).setType(Material.WOOD);
			
			w.getBlockAt(x, ymin + 4, zmin + 1).setType(Material.WOOD_STEP);
			w.getBlockAt(x, ymin + 4, zmax - 1).setType(Material.WOOD_STEP);
		}
		
		for ( int x=xmin; x <= xmax; x++ )
		{
			Block b = w.getBlockAt(x, ymin, zmin);
			b.setType(Material.WOOD_STAIRS);
			b.setData((byte)0x2);

			b = w.getBlockAt(x, ymin, zmax);
			b.setType(Material.WOOD_STAIRS);
			b.setData((byte)0x3);
		}

		for ( int z=zmin+2 ; z < zmax-1; z++ )
		{
			w.getBlockAt(xmin + 1, ymin, z).setType(Material.WOOD);
			w.getBlockAt(xmax - 1, ymin, z).setType(Material.WOOD);

			w.getBlockAt(xmin + 1, ymin + 4, z).setType(Material.WOOD_STEP);
			w.getBlockAt(xmax - 1, ymin + 4, z).setType(Material.WOOD_STEP);
		}
		
		for ( int z=zmin; z <= zmax; z++ )
		{
			Block b = w.getBlockAt(xmin, ymin, z);
			b.setType(Material.WOOD_STAIRS);
			b.setData((byte)0x0);

			b = w.getBlockAt(xmax, ymin, z);
			b.setType(Material.WOOD_STAIRS);
			b.setData((byte)0x1);
		}

		for ( int x=xmin + 2; x <= xmax - 2; x++ )
			for ( int z=zmin + 2; z <= zmax - 2; z++ )
			{
				w.getBlockAt(x, ymin, z).setType(Material.LAPIS_BLOCK);
				w.getBlockAt(x, ymin + 4, z).setType(Material.WOOD);
			}

		for ( int y=ymin+1; y<ymin+4; y++ )
		{
			w.getBlockAt(xmin+1, y, zmin+1).setType(Material.FENCE);
			w.getBlockAt(xmax-1, y, zmin+1).setType(Material.FENCE);
			w.getBlockAt(xmin+1, y, zmax-1).setType(Material.FENCE);
			w.getBlockAt(xmax-1, y, zmax-1).setType(Material.FENCE);
		}

		w.getBlockAt(dropOffCenter.getBlockX(), ymin + 4, dropOffCenter.getBlockZ()).setType(Material.GLOWSTONE);
		w.getBlockAt(dropOffCenter.getBlockX(), ymin + 5, dropOffCenter.getBlockZ()).setType(Material.WOOD_STEP);

		w.getBlockAt(xmin + 2, ymin + 5, zmin + 2).setType(Material.TORCH);
		w.getBlockAt(xmax - 2, ymin + 5, zmin + 2).setType(Material.TORCH);
		w.getBlockAt(xmin + 2, ymin + 5, zmax - 2).setType(Material.TORCH);
		w.getBlockAt(xmax - 2, ymin + 5, zmax - 2).setType(Material.TORCH);
	}
	
	@Override
	public boolean isAllowedToRespawn(Player player) { return true; }
	
	@Override
	public boolean useDiscreetDeathMessages() { return false; }

	private Location getSpawnLocationForTeam(int team)
	{
		Location loc;
		switch ( team )
		{
			case 0:
				if ( numTeams == 3 )
					loc = dropOffCenter.clone().add(-34.5, 0, -20.5); // for 3 teams, ensure they're equidistant from each other, as well as from the plinth
				else
					loc = dropOffCenter.clone().add(-40.5, 0, 0.5);
				loc.setYaw(-90);
				return loc;
			case 1:
				if ( numTeams == 3 )
					loc = dropOffCenter.clone().add(34.5, 0, -20.5); // for 3 teams, ensure they're equidistant from each other, as well as from the plinth
				else
					loc = dropOffCenter.clone().add(40.5, 0, 0);
				loc.setYaw(90);
				return loc;
			case 2:
				loc = dropOffCenter.clone().add(0.5, 0, 40.5);
				loc.setYaw(180);
				return loc;
			case 3:
				loc = dropOffCenter.clone().add(0.5, 0, -40.5);
				loc.setYaw(0);
				return loc;
			default:
				return dropOffCenter;
		}
	}
	
	@Override
	public Location getSpawnLocation(Player player)
	{
		return getSpawnLocationForTeam(getTeam(player));
	}
	
	@Override
	public void gameStarted()
	{
		teamScores = new int[numTeams];
		for ( int i=0; i<numTeams; i++ )
			teamScores[i] = 0;
		scoresForTypes.clear();
			
		int[] teamCounts = new int[numTeams];
		List<Player> players = getOnlinePlayers();
		
		while ( players.size() > 0 )
		{// pick random player, add them to one of the teams with the fewest players (picked randomly)
			Player player = players.remove(random.nextInt(players.size()));
			allocatePlayer(player, teamCounts);
		}
		
		broadcastMessage(ChatColor.YELLOW + "Day 1 of " + dayLimit);
		dayCountProcessID = getPlugin().getServer().getScheduler().scheduleSyncRepeatingTask(getPlugin(), new Runnable() {
			long lastRun = 0;
			public void run()
			{
				long time = getPlugin().getServer().getWorlds().get(0).getTime();
				
				if ( time < lastRun ) // time of day has gone backwards: must be a new day! Allocate the killers
				{
					dayCount ++;
					if ( dayCount >= dayLimit )
					{
						endGame();
						getPlugin().getServer().getScheduler().cancelTask(dayCountProcessID);
					}
					else
						broadcastMessage(ChatColor.YELLOW + "Day " + (dayCount+1) + " of " + dayLimit);
				}
				
				lastRun = time;
			}
		}, 600L, 100L); // initial wait: 30s, then check every 5s
	}
	
	private void endGame()
	{
		String message = ChatColor.YELLOW + "Time's up! The final scores are:";
		
		for ( int i=0; i<teamScores.length; i++ )
			 message += "\n" + getTeamChatColor(i) + getTeamName(i) + ": " + ChatColor.RESET + teamScores[i] + " points";

		int winningTeam = getHighestValueIndex(teamScores);
		message += "\n\nThe " + getTeamChatColor(winningTeam) + getTeamName(winningTeam) + ChatColor.RESET + " wins!";
		
		broadcastMessage(message);
		finishGame();
	}
	
	@Override
	public void gameFinished()
	{
		if ( dayCountProcessID != -1 )
		{
			getPlugin().getServer().getScheduler().cancelTask(dayCountProcessID);
			dayCountProcessID = -1;
		}
	}
	
	@Override
	public void playerJoinedLate(Player player, boolean isNewPlayer)
	{
		if ( !isNewPlayer )
			return;
		
		// put this player onto one of the teams with the fewest survivors
		int[] teamCounts = new int[numTeams];
		for ( int i=0; i<numTeams; i++ )
			teamCounts[i] = getOnlinePlayers(i, true).size();
		
		int team = allocatePlayer(player, teamCounts);		
		broadcastMessage(player, player.getName() + " has joined the " + getTeamChatColor(team) + getTeamName(team));
	}

	private int allocatePlayer(Player player, int[] teamCounts)
	{
		int team = getLowestValueIndex(teamCounts);
		
		setTeam(player, team);
		teamCounts[team] ++;
		player.sendMessage("You are on the " + getTeamChatColor(team) + getTeamName(team) + "\n" + ChatColor.RESET + "Use the /team command to send messages to your team only");
		
		equipPlayer(player, team);

		return team;
	}
	
	private void equipPlayer(Player player, int team)
	{
		PlayerInventory inv = player.getInventory();
		int color = getTeamItemColor(team);
		
		// give them team-dyed armor, and a sword
		ItemStack armor = new ItemStack(Material.LEATHER_CHESTPLATE);
		inv.setChestplate(setColor(armor, color));
		
		armor = new ItemStack(Material.LEATHER_CHESTPLATE);
		inv.setLeggings(setColor(armor, color));
		
		armor = new ItemStack(Material.LEATHER_BOOTS);
		inv.setBoots(setColor(armor, color));
		
		inv.addItem(new ItemStack(Material.IRON_SWORD));
	}

	private boolean isAllowedToDrop(Material type)
	{
		return type != Material.LEATHER_CHESTPLATE
			&& type != Material.LEATHER_LEGGINGS
			&& type != Material.LEATHER_BOOTS;
	}
	
	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event)
	{
		final Player player = event.getPlayer();
		getPlugin().getServer().getScheduler().scheduleSyncDelayedTask(getPlugin(), new Runnable() {
			public void run() {
				equipPlayer(player, getTeam(player));
			}
		});
	}
	
	@EventHandler
	public void onPlayerKilled(PlayerDeathEvent event)
	{
		// players don't drop leather armor or iron swords on death
		List<ItemStack> drops = event.getDrops();
		for ( int i=0; i<drops.size(); i++ )
			if ( !isAllowedToDrop(drops.get(i).getType()) )	
				drops.remove(i--);
	}
	
	@Override
	public void playerKilledOrQuit(OfflinePlayer player) { }
	
	@Override
	public Location getCompassTarget(Player player)
	{
		return null;
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onItemDrop(PlayerDropItemEvent event)
    {
		if ( shouldIgnoreEvent(event.getPlayer()) )
			return;
			
		if ( !isInDropOffArea(event.getPlayer().getLocation()) )
			return;
		
		ItemStack stack = event.getItemDrop().getItemStack();
		if ( !isAllowedToDrop(stack.getType()) )
		{
			event.setCancelled(true);
			return;
		}
		if ( !isScoringItemType(stack.getType()) )
			return;
		
		int team = getTeam(event.getPlayer());
		long dropScore = 0;
		
		for ( int i=0; i<stack.getAmount(); i++ )
			dropScore += getScoreForItem(stack.getType(), team);
		
		event.getPlayer().sendMessage(stack.getType().name() + ": score +" + dropScore);
		teamScores[team] += dropScore;
		
		event.getItemDrop().remove(); // don't actually DROP the item ... should we schedule a brief delay here? 
    }

	private boolean isInDropOffArea(Location loc)
	{
		if ( loc.getY() < dropOffCenter.getY() || loc.getY() > dropOffCenter.getY() + 4 )
			return false;
		
		return loc.getX() > dropOffCenter.getX() - 3 && loc.getX() < dropOffCenter.getX() + 4 
		    && loc.getZ() > dropOffCenter.getZ() - 3 && loc.getZ() < dropOffCenter.getZ() + 4;
	}
	
	private boolean isScoringItemType(Material type)
	{
		switch ( type )
		{
		case WHEAT:
		case CARROT_ITEM:
		case POTATO_ITEM:
		//case SEEDS:
		//case PUMPKIN_SEEDS:
		//case MELON_SEEDS:
		case MELON:
		case MELON_BLOCK:
		case PUMPKIN:
		case APPLE:
		case SUGAR_CANE:
		case BROWN_MUSHROOM:
		case RED_MUSHROOM:
		case EGG:
		case COCOA:
		case PORK:
		case RAW_BEEF:
		case RAW_CHICKEN:
		case RAW_FISH:
		case GRILLED_PORK:
		case COOKED_BEEF:
		case COOKED_CHICKEN:
		case COOKED_FISH:
		case WOOL:
			return true;
		default:
			return false;
		}
	}
	
	static final long startingScoreForType = 100, minScoreForType = 50;
	Map<Material, Long> scoresForTypes = new HashMap<Material, Long>();
	private long getScoreForItem(Material type, int team)
	{
		if ( !getOption(diminishingReturns).isEnabled() )
			return startingScoreForType;
		
		if ( scoresForTypes.containsKey(type) )
		{
			long retVal = scoresForTypes.get(type);
			scoresForTypes.put(type, retVal-1);
			return retVal;
		}
		else
		{
			scoresForTypes.put(type, startingScoreForType-1);
			return startingScoreForType;
		}
	}
	
	@EventHandler(ignoreCancelled = true)
	public void entityDamaged(EntityDamageEvent event)
	{
		if ( shouldIgnoreEvent(event.getEntity()) )
			return;
		
		if ( getOption(friendlyFire).isEnabled() )
			return;
		
		Player victim = (Player)event.getEntity();
		if ( victim == null )
			return;
		
		Player attacker = getAttacker(event);
		if ( attacker == null )
			return;
		
		if ( getTeam(victim) == getTeam(attacker) )
			event.setCancelled(true);
	}

	@Override
	public void toggleOption(int num)
	{
		super.toggleOption(num);
		
		toggleOption_ensureOnlyOneEnabled(num, optionTwoTeams, optionThreeTeams, optionFourTeams);
		if ( num >= optionTwoTeams && num <= optionFourTeams && getOption(num).isEnabled() )
			numTeams = num; // change the numTeams value ... it's a happy coincidence that optionTwoTeams = 2, optionThreeTeams = 3, optionFourTeams = 4

		toggleOption_ensureOnlyOneEnabled(num, optionTwoDays, optionFourDays, optionSixDays);
		if ( num >= optionTwoDays && num <= optionSixDays && getOption(num).isEnabled() )
			switch ( num )
			{
				case optionTwoDays:
					dayLimit = 2; break;
				case optionFourDays:
					dayLimit = 4; break;
				case optionSixDays:
					dayLimit = 6; break;
				case optionEightDays:
					dayLimit = 8; break;
			}
	}
}
