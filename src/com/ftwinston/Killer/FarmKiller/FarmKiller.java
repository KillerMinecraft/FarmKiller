package com.ftwinston.Killer.FarmKiller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.ftwinston.Killer.GameMode;
import com.ftwinston.Killer.Option;

import net.minecraft.server.ChunkCoordinates;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Material;

import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;

public class FarmKiller extends GameMode
{
	public static final int friendlyFire = 0, diminishingReturns = 1, optionTwoTeams = 2, optionThreeTeams = 3, optionFourTeams = 4, optionAnnounceScores = 5, optionTwoDays = 6, optionFourDays = 7, optionSixDays = 8, optionEightDays = 9;

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
			new Option("Announce scores at the start of each day", true),
			new Option("Game lasts for two days", false),
			new Option("Game lasts for four days", true),
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

			case 3:
				return "You will respawn at your base when you die.";
				
			case 4:
				if ( getOption(optionAnnounceScores).isEnabled() )
					return "The current scores will be announced at the start of each day.";
					
			default:
				return null;
		}
	}
	
	@Override
	public boolean teamAllocationIsSecret() { return false; }
	
	@Override
	public Environment[] getWorldsToGenerate() { return new Environment[] { Environment.NORMAL }; }
	
	private Location dropOffCenter = null;
	
	@Override
	public BlockPopulator[] getExtraBlockPopulators(int worldNumber)
	{
		if ( worldNumber != 0 )
			return null;
		
		return new BlockPopulator[] { new PlateauGenerator() };
	}
	
	class PlateauGenerator extends BlockPopulator
	{
		int cDropOffX, cDropOffZ, cMinX, cMaxX, cMinZ, cMaxZ;		
		
		@Override
		public void populate(World w, Random r, Chunk c)
		{
			if ( dropOffCenter == null )
			{
				ChunkCoordinates dropOff = ((CraftWorld)w).getHandle().getSpawn();
				cDropOffX = dropOff.x >> 4; cDropOffZ = dropOff.z >> 4;
				
				dropOffCenter = new Location(w, cDropOffX * 16 + 7, w.getSeaLevel() + 10, cDropOffZ * 16 + 7);
				
				cMinX = cDropOffX - 4; cMaxX = cDropOffX + 4;
				cMinZ = cDropOffZ - 4; cMaxZ = cDropOffZ + 4;
			}
			
			int cx = c.getX(), cz = c.getZ();
			if ( cx < cMinX || cx > cMaxX || cz < cMinZ || cz > cMaxZ)
				return;
			
			// create a grassy plain around where the drop off will be, fading to the height of the "underlying" world at the edges
			if ( cx == cMinX || cx == cMaxX || cz == cMinZ || cz == cMaxZ )
				createSlope(c);
			else
				createFlatSurface(c);
			
			// generate the central drop-off point itself
			if ( c.getX() == cDropOffX && c.getZ() == cDropOffZ )
				createDropOffPoint(c);
			
			// generate spawn points for each team
			for ( int team=0; team<numTeams; team++ )
			{
				Location spawn = getSpawnLocationForTeam(team);
				if ( spawn.getChunk() == c )
				{
					int spawnX = spawn.getBlockX() & 15, spawnY = spawn.getBlockY() - 1, spawnZ = spawn.getBlockZ() & 15;
					
					for ( int x = spawnX-2; x<=spawnX+2; x++ )
						for ( int z = spawnZ-2; z<=spawnZ+2; z++ )
						{
							Block b = c.getBlock(x, spawnY, z);
							
							b.setType(Material.WOOL);
							b.setData(getTeamWoolColor(team));
						}
					c.getBlock(spawnX, spawnY, spawnZ).setType(Material.BEDROCK);
				}
			}
		}
		
		public void createFlatSurface(Chunk c)
		{
			int plateauY = dropOffCenter.getBlockY() - 1;
			for ( int x=0; x<16; x++ )
				for ( int z=0; z<16; z++ )
					fillInAboveBelow(c, x, plateauY, z);
		}
		
		public void createSlope(Chunk c)
		{	
			int cx = c.getX(), cz = c.getZ(), plateauY = dropOffCenter.getBlockY() - 1;
			
			if ( cx == cMinX )
			{
				if ( cz == cMinZ )
					for ( int x=0; x<16; x++ )
						for ( int z=0; z<16; z++ )
						{
							float fraction = Math.max(((float)15-x)/16, ((float)15-z)/16);	
							int y = (int)(0.5f + getHighestGroundYAt(c, x, z) * fraction + (plateauY) * (1f - fraction));
							fillInAboveBelow(c, x, y, z);
						}
				else if ( cz == cMaxZ )
					for ( int x=0; x<16; x++ )
						for ( int z=0; z<16; z++ )
						{
							float fraction = Math.max(((float)15-x)/16, ((float)z)/16);	
							int y = (int)(0.5f + getHighestGroundYAt(c, x, z) * fraction + (plateauY) * (1f - fraction));
							fillInAboveBelow(c, x, y, z);
						}
				else
					for ( int x=0; x<16; x++ )
					{
						float fraction = ((float)15-x)/16;
						for ( int z=0; z<16; z++ )
						{
							int y = (int)(0.5f + getHighestGroundYAt(c, x, z) * fraction + (plateauY) * (1f - fraction));
							fillInAboveBelow(c, x, y, z);
						}
					}
			}
			else if ( cx == cMaxX )
			{
				if ( cz == cMinZ )
					for ( int x=0; x<16; x++ )
						for ( int z=0; z<16; z++ )
						{
							float fraction = Math.max(((float)x)/16, ((float)15-z)/16);	
							int y = (int)(0.5f + getHighestGroundYAt(c, x, z) * fraction + (plateauY) * (1f - fraction));
							fillInAboveBelow(c, x, y, z);
						}
				else if ( cz == cMaxZ )
					for ( int x=0; x<16; x++ )
						for ( int z=0; z<16; z++ )
						{
							float fraction = Math.max(((float)x)/16, ((float)z)/16);	
							int y = (int)(0.5f + getHighestGroundYAt(c, x, z) * fraction + (plateauY) * (1f - fraction));
							fillInAboveBelow(c, x, y, z);
						}
				else
					for ( int x=0; x<16; x++ )
					{
						float fraction = ((float)x)/16;
						for ( int z=0; z<16; z++ )
						{
							int y = (int)(0.5f + getHighestGroundYAt(c, x, z) * fraction + (plateauY) * (1f - fraction));
							fillInAboveBelow(c, x, y, z);
						}
					}
			}
			else if ( cz == cMinZ )
				for ( int z=0; z<16; z++ )
				{
					float fraction = ((float)15-z)/16;
					for ( int x=0; x<16; x++ )
					{
						int y = (int)(0.5f + getHighestGroundYAt(c, x, z) * fraction + (plateauY) * (1f - fraction));
						fillInAboveBelow(c, x, y, z);
					}
				}
			else if ( cz == cMaxZ )
				for ( int z=0; z<16; z++ )
				{
					float fraction = ((float)z)/16;
					for ( int x=0; x<16; x++ )
					{
						int y = (int)(0.5f + getHighestGroundYAt(c, x, z) * fraction + (plateauY) * (1f - fraction));
						fillInAboveBelow(c, x, y, z);
					}
				}
				
		}
		
		public int getHighestBlockYAt(Chunk c, int x, int z)
		{
			return ((CraftChunk)c).getHandle().b(x & 15, z & 15);
		}
		
		private int getHighestGroundYAt(Chunk c, int x, int z)
		{	
			int y = getHighestBlockYAt(c, x, z);
			x = x & 15; z = z & 15;
			Block b = c.getBlock(x, y, z);
			
			int seaLevel = c.getWorld().getSeaLevel();
			while ( y > seaLevel )
			{
				if ( b.getType() == Material.GRASS || b.getType() == Material.DIRT || b.getType() == Material.STONE || b.getType() == Material.SAND || b.getType() == Material.GRAVEL || b.getType() == Material.BEDROCK )
					break;

				y--;
				b = c.getBlock(x, y, z);
			}

			return y;
		}
		
		private void fillInAboveBelow(Chunk c, int x, int groundY, int z)
		{
			int y = groundY-1, maxY = c.getWorld().getMaxHeight();
			Block b = c.getBlock(x, y, z);
			do
			{
				b.setType(Material.DIRT);
				
				y--;
				b = c.getBlock(x, y, z);
			}
			while ( b.getType() != Material.DIRT && b.getType() != Material.STONE &&  b.getType() != Material.SAND && b.getType() != Material.GRAVEL && b.getType() != Material.BEDROCK );
			
			int prevMaxY = getHighestBlockYAt(c, x, z);
			for ( y=prevMaxY; y<groundY; y++ )
				c.getBlock(x,y,z).setType(Material.DIRT);
			
			c.getBlock(x,groundY,z).setType(Material.GRASS);
			
			for ( y=groundY+1; y<maxY; y++ )
				c.getBlock(x,y,z).setType(Material.AIR);
		}
		
		public void createDropOffPoint(Chunk c)
		{
			int xmin = dropOffCenter.getBlockX() - 3, xmax = dropOffCenter.getBlockX() + 3;
			int zmin = dropOffCenter.getBlockZ() - 3, zmax = dropOffCenter.getBlockZ() + 3;
			int ymin = dropOffCenter.getBlockY();

			for ( int x=xmin-4; x <= xmax + 4; x++ )
				for ( int z=zmin-4; z <= zmax + 4; z++ )
					c.getBlock(x, ymin-1, z).setType(Material.GRAVEL);
			
			// now, generate a hut for the drop-off
			for ( int x=xmin+1; x < xmax; x++ )
			{
				c.getBlock(x, ymin, zmin + 1).setType(Material.WOOD);
				c.getBlock(x, ymin, zmax - 1).setType(Material.WOOD);
				
				c.getBlock(x, ymin + 4, zmin + 1).setType(Material.WOOD_STEP);
				c.getBlock(x, ymin + 4, zmax - 1).setType(Material.WOOD_STEP);
			}
			
			for ( int x=xmin; x <= xmax; x++ )
			{
				Block b = c.getBlock(x, ymin, zmin);
				b.setType(Material.WOOD_STAIRS);
				b.setData((byte)0x2);

				b = c.getBlock(x, ymin, zmax);
				b.setType(Material.WOOD_STAIRS);
				b.setData((byte)0x3);
			}

			for ( int z=zmin+2 ; z < zmax-1; z++ )
			{
				c.getBlock(xmin + 1, ymin, z).setType(Material.WOOD);
				c.getBlock(xmax - 1, ymin, z).setType(Material.WOOD);

				c.getBlock(xmin + 1, ymin + 4, z).setType(Material.WOOD_STEP);
				c.getBlock(xmax - 1, ymin + 4, z).setType(Material.WOOD_STEP);
			}
			
			for ( int z=zmin; z <= zmax; z++ )
			{
				Block b = c.getBlock(xmin, ymin, z);
				b.setType(Material.WOOD_STAIRS);
				b.setData((byte)0x0);

				b = c.getBlock(xmax, ymin, z);
				b.setType(Material.WOOD_STAIRS);
				b.setData((byte)0x1);
			}

			for ( int x=xmin + 2; x <= xmax - 2; x++ )
				for ( int z=zmin + 2; z <= zmax - 2; z++ )
				{
					c.getBlock(x, ymin, z).setType(Material.LAPIS_BLOCK);
					c.getBlock(x, ymin + 4, z).setType(Material.WOOD);
				}

			for ( int y=ymin+1; y<ymin+4; y++ )
			{
				c.getBlock(xmin+1, y, zmin+1).setType(Material.FENCE);
				c.getBlock(xmax-1, y, zmin+1).setType(Material.FENCE);
				c.getBlock(xmin+1, y, zmax-1).setType(Material.FENCE);
				c.getBlock(xmax-1, y, zmax-1).setType(Material.FENCE);
			}

			c.getBlock(dropOffCenter.getBlockX(), ymin + 4, dropOffCenter.getBlockZ()).setType(Material.GLOWSTONE);
			c.getBlock(dropOffCenter.getBlockX(), ymin + 5, dropOffCenter.getBlockZ()).setType(Material.WOOD_STEP);

			c.getBlock(xmin + 2, ymin + 5, zmin + 2).setType(Material.TORCH);
			c.getBlock(xmax - 2, ymin + 5, zmin + 2).setType(Material.TORCH);
			c.getBlock(xmin + 2, ymin + 5, zmax - 2).setType(Material.TORCH);
			c.getBlock(xmax - 2, ymin + 5, zmax - 2).setType(Material.TORCH);
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
					loc = dropOffCenter.clone().add(-35.5, 0, -20.5); // for 3 teams, ensure they're equidistant from each other, as well as from the plinth
				else
					loc = dropOffCenter.clone().add(-43.5, 0, 0.5);
				loc.setYaw(-90);
				return loc;
			case 1:
				if ( numTeams == 3 )
					loc = dropOffCenter.clone().add(35.5, 0, -20.5); // for 3 teams, ensure they're equidistant from each other, as well as from the plinth
				else
					loc = dropOffCenter.clone().add(43.5, 0, 0);
				loc.setYaw(90);
				return loc;
			case 2:
				loc = dropOffCenter.clone().add(0.5, 0, 43.5);
				loc.setYaw(180);
				return loc;
			case 3:
				loc = dropOffCenter.clone().add(0.5, 0, -43.5);
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
					{
						String message = ChatColor.YELLOW + "Day " + (dayCount+1) + " of " + dayLimit;
						if ( getOption(optionAnnounceScores).isEnabled() )
							message += writeCurrentScores();
						broadcastMessage(message);
					}
				}
				
				lastRun = time;
			}
		}, 600L, 100L); // initial wait: 30s, then check every 5s
	}
	
	private void endGame()
	{
		String message = ChatColor.YELLOW + "Time's up! The final scores are:" + writeCurrentScores();
		broadcastMessage(message);
		finishGame();
	}
	
	private String writeCurrentScores()
	{
		String message = "";
		for ( int i=0; i<teamScores.length; i++ )
			 message += "\n" + getTeamChatColor(i) + getTeamName(i) + ": " + ChatColor.RESET + teamScores[i] + " points";

		int winningTeam = getHighestValueIndex(teamScores);
		message += "\n\nThe " + getTeamChatColor(winningTeam) + getTeamName(winningTeam) + ChatColor.RESET + " wins!";
		
		return message;
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
		
		Option.ensureOnlyOneEnabled(getOptions(), num, optionTwoTeams, optionThreeTeams, optionFourTeams);
		if ( num >= optionTwoTeams && num <= optionFourTeams && getOption(num).isEnabled() )
			numTeams = num; // change the numTeams value ... it's a happy coincidence that optionTwoTeams = 2, optionThreeTeams = 3, optionFourTeams = 4

		Option.ensureOnlyOneEnabled(getOptions(), num, optionTwoDays, optionFourDays, optionSixDays, optionEightDays);
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
