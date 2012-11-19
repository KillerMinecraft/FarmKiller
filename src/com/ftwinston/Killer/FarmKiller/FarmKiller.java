package com.ftwinston.Killer.FarmKiller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ftwinston.Killer.GameMode;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
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
	public static final int friendlyFire = 0, diminishingReturns = 1, optionTwoTeams = 2, optionThreeTeams = 3, optionFourTeams = 4;

	@Override
	public String getName() { return "Farm Killer"; }
	
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
			new Option("Four teams", false)
		};
		
		return options;
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
	
	public String getTeamName(int team)
	{
		switch ( team )
		{
		case 0:
			return "blue team";
		case 1:
			return "red team";
		case 2:
			return "yellow team";
		case 3:
			return "green team";
		default:
			return "team #" + team;
		}
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
						numText = ""; break;
				}
				return "Players have been split into " + numText + "teams. Get farming!\nThe scoreboard shows what team each player is on.";
			}
			case 1:
				String message = "The teams complete to deliver the most farm produce (plants, animals, and eggs - no meat or seeds) to a central depot.";
				return message;
				
			default:
				return null;
		}
	}
	
	@Override
	public boolean teamAllocationIsSecret() { return false; }
	
	@Override
	public boolean usesNether() { return false; }
	
	private Location dropOffCenter;
	
	@Override
	public void worldGenerationComplete(World main, World nether)
	{
		dropOffCenter = new Location(main, 0, main.getSeaLevel()+10, 0);
		
		// create a grassy plain around where the drop off will be
		createFlatSurface();
		
		// generate the central drop-off point itself
		createDropOff();
	
		// generate spawn points for each team
		for ( int team=0; team<numTeams; team++ )
		{
			Location spawn = getSpawnLocationForTeam(team);
			spawn.getBlock().getRelative(BlockFace.DOWN).setType(Material.BEDROCK);
		}
	}
	
	@Override
	public boolean isLocationProtected(Location l)
	{
		int cy = dropOffCenter.getBlockY(), ly = l.getBlockY();
		if ( l.getBlockY() < cy - 2 || ly > cy + 5 )
			return false;
		
		// the drop-off building is protected
		int cx =  dropOffCenter.getBlockX(), cz = dropOffCenter.getBlockZ(), lx = l.getBlockX(), lz = l.getBlockZ();
		if ( lx > cx - 3 && lx < cx + 3
		  && lz > cz - 3 && lz < cz + 3 )
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
		
		int dropOffY = dropOffCenter.getBlockY();
		World world = dropOffCenter.getWorld();
		
		for ( int x=dropOffCenter.getBlockX()-range; x<=dropOffCenter.getBlockX()+range; x++ )
			for ( int z=dropOffCenter.getBlockZ()-range; z<=dropOffCenter.getBlockZ()+range; z++ )
				fillInAboveBelow(world, x, dropOffY-1, z);
		
		int minZ = dropOffCenter.getBlockZ()-range, maxZ = dropOffCenter.getBlockZ()+range,
			minX = dropOffCenter.getBlockX()-range, maxX = dropOffCenter.getBlockX()+range;
		for ( int fade = 1; fade<=fadeLength; fade++ )
		{
			float fraction = ((float)fade)/fadeLength;
			for ( int z=minZ-fadeLength; z<=maxZ+fadeLength; z++ )
			{
				int x = dropOffCenter.getBlockX()+range+fade;
				// only do corners up to (and including) the "diagonal"
				
				int y = (int)(0.5f + world.getHighestBlockYAt(x, z) * fraction + (dropOffY-1) * (1f - fraction));
				fillInAboveBelow(world, x, y, z);
				
				x = dropOffCenter.getBlockX()-range-fade;
				y = (int)(0.5f + world.getHighestBlockYAt(x, z) * fraction + (dropOffY-1) * (1f - fraction));
				fillInAboveBelow(world, x, y, z);
			}
			
			for ( int x=minX-fadeLength; x<=maxX+fadeLength; x++ )
			{
				int z = dropOffCenter.getBlockZ()+range+fade;
				// only do corners up to (but not including) the "diagonal"
				
				int y = (int)(0.5f + world.getHighestBlockYAt(x, z) * fraction + (dropOffY-1) * (1f - fraction));
				fillInAboveBelow(world, x, y, z);
				
				z = dropOffCenter.getBlockZ()-range-fade;
				y = (int)(0.5f + world.getHighestBlockYAt(x, z) * fraction + (dropOffY-1) * (1f - fraction));
				fillInAboveBelow(world, x, y, z);
			}
		}
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
		int xmin = dropOffCenter.getBlockX() - 2, xmax = dropOffCenter.getBlockX() + 2;
		int zmin = dropOffCenter.getBlockZ() - 2, zmax = dropOffCenter.getBlockZ() + 2;
		int ymin = dropOffCenter.getBlockY();

		World w = dropOffCenter.getWorld();
		
		// now, generate a hut for the drop-off
		w.getBlockAt(xmin, ymin, zmin).setType(Material.WOOD_STEP);
		w.getBlockAt(xmax, ymin, zmin).setType(Material.WOOD_STEP);
		w.getBlockAt(xmin, ymin, zmax).setType(Material.WOOD_STEP);
		w.getBlockAt(xmax, ymin, zmax).setType(Material.WOOD_STEP);
		for ( int x=xmin + 1; x < xmax; x++ )
		{
			Block b = w.getBlockAt(x, ymin, zmin);
			b.setType(Material.WOOD_STAIRS);
			b.setData((byte)0x3);

			w.getBlockAt(x, ymin, zmin + 1).setType(Material.WOOD);

			b = w.getBlockAt(x, ymin, zmax);
			b.setType(Material.WOOD_STAIRS);
			b.setData((byte)0x2);

			w.getBlockAt(x, ymin, zmax - 1).setType(Material.WOOD);
		}

		for ( int z=zmin + 1; z < zmax; z++ )
		{
			Block b = w.getBlockAt(xmin, ymin, z);
			b.setType(Material.WOOD_STAIRS);
			b.setData((byte)0x0);

			w.getBlockAt(xmin + 1, ymin, z).setType(Material.WOOD);
			w.getBlockAt(xmin + 1, ymin + 4, z).setType(Material.WOOD_STEP);

			b = w.getBlockAt(xmax, ymin, z);
			b.setType(Material.WOOD_STAIRS);
			b.setData((byte)0x1);

			w.getBlockAt(xmax - 1, ymin, z).setType(Material.WOOD);
			w.getBlockAt(xmax - 1, ymin + 4, z).setType(Material.WOOD_STEP);
		}

		for ( int x=xmin + 2; x < xmax - 1; x++ )
			for ( int z=zmin + 2; z < zmax - 1; z++ )
			{
				w.getBlockAt(x, ymin, z).setType(Material.LAPIS_BLOCK);
				w.getBlockAt(x, ymin + 4, z).setType(Material.WOOD);
			}

		for ( int y=ymin+1; y<ymin+4; y++ )
		{
			w.getBlockAt(xmin+2, y, zmin+2).setType(Material.FENCE);
			w.getBlockAt(xmax-2, y, zmin+2).setType(Material.FENCE);
			w.getBlockAt(xmin+2, y, zmax-2).setType(Material.FENCE);
			w.getBlockAt(xmax-2, y, zmax-2).setType(Material.FENCE);
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
		switch ( team )
		{
			case 0:
				if ( numTeams == 3 )
					return dropOffCenter.add(-34, 0, -20); // for 3 teams, ensure they're equidistant from each other, as well as from the plinth
				else
					return dropOffCenter.add(-40, 0, 0);
			case 1:
				if ( numTeams == 3 )
					return dropOffCenter.add(34, 0, -20); // for 3 teams, ensure they're equidistant from each other, as well as from the plinth
				else
					return dropOffCenter.add(40, 0, 0);
			case 2:
				return dropOffCenter.add(0, 0, 40);
			case 3:
				return dropOffCenter.add(0, 0, -40);
			default:
				return dropOffCenter;
		}
	}
	
	@Override
	public Location getSpawnLocation(Player player)
	{
		return getSpawnLocationForTeam(getTeam(player));
	}
	
	private int pickSmallestTeam(int[] teamCounts)
	{
		// determining which team(s) have the fewest players in this way means the same one won't always be undersized.
		boolean[] candidateTeams = new boolean[numTeams];
		int fewest = 0, numCandidates = 0;
		for ( int i=0; i<numTeams; i++ )
		{
			int num = teamCounts[i];
			if ( num == fewest )
			{
				candidateTeams[i] = true;
				numCandidates ++;
			}
			else if ( num < fewest )
			{
				candidateTeams[i] = true;
				fewest = num;
				numCandidates = 1;
				
				// clear previous candidates
				for ( int j=0; j<i; j++ )
					candidateTeams[j] = false;
			}
			else
				candidateTeams[i] = false;
		}
		
		// add them to one of the candidates
		int candidatesToSkip = random.nextInt(numCandidates);
		for ( int i=0; i<numTeams; i++ )
			if ( candidateTeams[i] )
			{
				if ( candidatesToSkip == 0 )
					return i;
				candidatesToSkip --;
			}
		
		// should never get here, but ... who knows
		return random.nextInt(teamCounts.length);
	}
	
	long[] teamScores;
	
	@Override
	public void gameStarted()
	{
		teamScores = new long[numTeams];
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
	}
	
	@Override
	public void gameFinished()
	{
		
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
		int team = pickSmallestTeam(teamCounts);
		
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
		
		event.getPlayer().sendMessage("Score +" + dropScore);
		teamScores[team] += dropScore;
		
		event.getItemDrop().remove(); // don't actually DROP the item ... should we schedule a brief delay here? Only if 
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
		//case SEEDS:
		//case PUMPKIN_SEEDS:
		//case MELON_SEEDS:
		case MELON:
		case MELON_BLOCK:
		case PUMPKIN:
		case SUGAR_CANE:
		case BROWN_MUSHROOM:
		case RED_MUSHROOM:
		case EGG:
		case COCOA:
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
	public boolean toggleOption(int num)
	{
		boolean retVal = super.toggleOption(num);
		
		int firstTeamOption = optionTwoTeams, lastTeamOption = optionFourTeams;
		if ( num < optionTwoTeams || num > optionFourTeams )
			return retVal;
		
		if ( retVal )
		{// turned on; turn the others off
			for ( int i=firstTeamOption; i<=lastTeamOption; i++ )
				if ( i != num )
					getOption(i).setEnabled(false);
			
			// change the numTeams value ... it's a happy coincidence that optionTwoTeams = 2, optionThreeTeams = 3, optionFourTeams = 4
			numTeams = num;
		}
		else
		{// turned off; if all are off, turn this one back on
			boolean allOff = true;
			for ( int i=optionTwoTeams; i<=lastTeamOption; i++ )
				if ( getOption(i).isEnabled() )
				{
					allOff = false;
					break;
				}
			if ( allOff )
			{
				getOption(num).setEnabled(true);
				retVal = true;
			}
		}
		return retVal;
	}
}
