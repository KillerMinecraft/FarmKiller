package com.ftwinston.KillerMinecraft.Modules.FarmKiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.Helper;
import com.ftwinston.KillerMinecraft.Option;
import com.ftwinston.KillerMinecraft.PlayerFilter;
import com.ftwinston.KillerMinecraft.WorldConfig;
import com.ftwinston.KillerMinecraft.Configuration.NumericOption;
import com.ftwinston.KillerMinecraft.Configuration.TeamInfo;
import com.ftwinston.KillerMinecraft.Configuration.ToggleOption;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.Material;

public class FarmKiller extends GameMode
{
	ToggleOption friendlyFire, diminishingReturns, announceScores;
	NumericOption numTeams, dayLimit;
	
	private int dayCountProcessID, dayCount = 0;
	
	private abstract class FarmTeamInfo extends TeamInfo
	{
		private long score = 0;
		public long getScore() { return score; }
		public void addScore(long add) { score += add; }
	}
	
	FarmTeamInfo[] teams = new FarmTeamInfo[0];
	
	private int indexOfTeam(TeamInfo team)
	{
		for (int i=0; i<teams.length; i++)
			if (team == teams[i])
				return i;
		return -1;
	}
	
	@Override
	public int getMinPlayers() { return numTeams.getValue(); } // one player on each team is our minimum
	
	@Override
	public Option[] setupOptions()
	{
		friendlyFire = new ToggleOption("Players can hurt teammates", true);
		diminishingReturns = new ToggleOption("Diminishing returns on each item type", true);
		announceScores = new ToggleOption("Announce scores at the start of each day", true);
		
		numTeams = new NumericOption("Number of teams", 2, 4, Material.CHEST, 2) {
			@Override
			protected void changed() 
			{
				FarmTeamInfo[] newTeams = new FarmTeamInfo[numTeams == null ? 2 : numTeams.getValue()];
				int i;
				for ( i=0; i<teams.length && i<newTeams.length; i++ )
					newTeams[i] = teams[i];
				for ( ; i<newTeams.length; i++ )
					switch ( i )
					{
						case 0:
							newTeams[i] = new FarmTeamInfo() {
								@Override
								public String getName() { return "red team"; }
								@Override
								public ChatColor getChatColor() { return ChatColor.RED; }
								@Override
								public byte getWoolColor() { return (byte)0xE; }
								@Override
								public Color getArmorColor() { return Color.RED; }
							}; break;
						case 1:
							newTeams[i] = new FarmTeamInfo() {
								@Override
								public String getName() { return "blue team"; }
								@Override
								public ChatColor getChatColor() { return ChatColor.BLUE; }
								@Override
								public byte getWoolColor() { return (byte)0xB; }
								@Override
								public Color getArmorColor() { return Color.fromRGB(0x0066FF); }
							}; break;
						case 2:
							newTeams[i] = new FarmTeamInfo() {
								@Override
								public String getName() { return "yellow team"; }
								@Override
								public ChatColor getChatColor() { return ChatColor.YELLOW; }
								@Override
								public byte getWoolColor() { return (byte)0x4; }
								@Override
								public Color getArmorColor() { return Color.YELLOW; }
							}; break;
						default:
							newTeams[i] = new FarmTeamInfo() {
								@Override
								public String getName() { return "green team"; }
								@Override
								public ChatColor getChatColor() { return ChatColor.GREEN; }
								@Override
								public byte getWoolColor() { return (byte)0x5; }
								@Override
								public Color getArmorColor() { return Color.GREEN; }
							}; break;
					}
				teams = newTeams;
				setTeams(teams);
			}
		};
		dayLimit = new NumericOption("Duration of game, in days", 2, 8, Material.WATCH, 4);			
		
		return new Option[] { friendlyFire, diminishingReturns, numTeams, announceScores, dayLimit };
	}
	
	@Override
	public String getHelpMessage(int num, TeamInfo team)
	{
		switch ( num )
		{
			case 0:
			{
				String numText;
				switch ( numTeams.getValue() )
				{
					case 2:
						numText = "two "; break;
					case 3:
						numText = "three "; break;
					case 4:
						numText = "four "; break;
					default:
						numText = Integer.toString(numTeams.getValue()); break;
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
				if ( announceScores.isEnabled() )
					return "The current scores will be announced at the start of each day.";
					
			default:
				return null;
		}
	}
	
	@Override
	public Environment[] getWorldsToGenerate() { return new Environment[] { Environment.NORMAL }; }
	
	private Location dropOffCenter = null;
	
	@Override
	public void beforeWorldGeneration(int worldNumber, WorldConfig world)
	{
		if ( worldNumber != 0 )
			return;
		
		generating = true;
		dropOffCenter = null;
		
		world.getExtraPopulators().add(new PlateauGenerator());
	}
	
	boolean generating = true;
	
	class PlateauGenerator extends BlockPopulator
	{
		int cDropOffX, cDropOffZ, cMinX, cMaxX, cMinZ, cMaxZ;		
		
		@SuppressWarnings("deprecation")
		@Override
		public void populate(World w, Random r, Chunk c)
		{
			if ( dropOffCenter == null )
			{
				cDropOffX = w.getSpawnLocation().getBlockX() >> 4; cDropOffZ = w.getSpawnLocation().getBlockZ() >> 4;
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
			TeamInfo[] teams = getTeams();
			for ( int teamNum=0; teamNum<numTeams.getValue(); teamNum++ )
			{
				TeamInfo team = teams[teamNum];
				Location spawn = getSpawnLocationForTeam(teamNum);
				if ( spawn.getChunk() == c )
				{
					int spawnX = spawn.getBlockX() & 15, spawnY = spawn.getBlockY() - 1, spawnZ = spawn.getBlockZ() & 15;
					
					for ( int x = spawnX-2; x<=spawnX+2; x++ )
						for ( int z = spawnZ-2; z<=spawnZ+2; z++ )
						{
							Block b = c.getBlock(x, spawnY, z);
							
							b.setType(Material.WOOL);
							b.setData(team.getWoolColor());
						}
					c.getBlock(spawnX, spawnY, spawnZ).setType(Material.BEDROCK);
				}
			}
		}
		
		private Material[] groundMats = { Material.GRASS, Material.DIRT, Material.STONE, Material.SAND, Material.GRAVEL, Material.BEDROCK };
		
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
							int y = (int)(0.5f + Helper.getHighestYAt(c, x, z, c.getWorld().getSeaLevel(), groundMats) * fraction + (plateauY) * (1f - fraction));
							fillInAboveBelow(c, x, y, z);
						}
				else if ( cz == cMaxZ )
					for ( int x=0; x<16; x++ )
						for ( int z=0; z<16; z++ )
						{
							float fraction = Math.max(((float)15-x)/16, ((float)z)/16);	
							int y = (int)(0.5f + Helper.getHighestYAt(c, x, z, c.getWorld().getSeaLevel(), groundMats) * fraction + (plateauY) * (1f - fraction));
							fillInAboveBelow(c, x, y, z);
						}
				else
					for ( int x=0; x<16; x++ )
					{
						float fraction = ((float)15-x)/16;
						for ( int z=0; z<16; z++ )
						{
							int y = (int)(0.5f + Helper.getHighestYAt(c, x, z, c.getWorld().getSeaLevel(), groundMats) * fraction + (plateauY) * (1f - fraction));
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
							int y = (int)(0.5f + Helper.getHighestYAt(c, x, z, c.getWorld().getSeaLevel(), groundMats) * fraction + (plateauY) * (1f - fraction));
							fillInAboveBelow(c, x, y, z);
						}
				else if ( cz == cMaxZ )
					for ( int x=0; x<16; x++ )
						for ( int z=0; z<16; z++ )
						{
							float fraction = Math.max(((float)x)/16, ((float)z)/16);	
							int y = (int)(0.5f + Helper.getHighestYAt(c, x, z, c.getWorld().getSeaLevel(), groundMats) * fraction + (plateauY) * (1f - fraction));
							fillInAboveBelow(c, x, y, z);
						}
				else
					for ( int x=0; x<16; x++ )
					{
						float fraction = ((float)x)/16;
						for ( int z=0; z<16; z++ )
						{
							int y = (int)(0.5f + Helper.getHighestYAt(c, x, z, c.getWorld().getSeaLevel(), groundMats) * fraction + (plateauY) * (1f - fraction));
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
						int y = (int)(0.5f + Helper.getHighestYAt(c, x, z, c.getWorld().getSeaLevel(), groundMats) * fraction + (plateauY) * (1f - fraction));
						fillInAboveBelow(c, x, y, z);
					}
				}
			else if ( cz == cMaxZ )
				for ( int z=0; z<16; z++ )
				{
					float fraction = ((float)z)/16;
					for ( int x=0; x<16; x++ )
					{
						int y = (int)(0.5f + Helper.getHighestYAt(c, x, z, c.getWorld().getSeaLevel(), groundMats) * fraction + (plateauY) * (1f - fraction));
						fillInAboveBelow(c, x, y, z);
					}
				}
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
			
			int prevMaxY = Helper.getHighestBlockYAt(c, x, z);
			for ( y=prevMaxY; y<groundY; y++ )
				c.getBlock(x,y,z).setType(Material.DIRT);
			
			c.getBlock(x,groundY,z).setType(Material.GRASS);
			
			for ( y=groundY+1; y<maxY; y++ )
				c.getBlock(x,y,z).setType(Material.AIR);
		}
		
		@SuppressWarnings("deprecation")
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
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event)
    {
		Location loc = event.getLocation();
		if ( dropOffCenter != null
		  && loc.getX() > dropOffCenter.getX() - 80 && loc.getX() < dropOffCenter.getX() + 80
		  && loc.getZ() > dropOffCenter.getZ() - 80 && loc.getZ() < dropOffCenter.getZ() + 80 )
		{
    		event.setCancelled(true);
		}
    }
	
	@Override
	public boolean isLocationProtected(Location l, Player p)
	{
		if ( dropOffCenter == null )
			return false;
		
		int cy = dropOffCenter.getBlockY(), ly = l.getBlockY();
		if ( l.getBlockY() < cy - 1 || ly > cy + 5 )
			return false;
		
		// the drop-off building is protected
		int cx =  dropOffCenter.getBlockX(), cz = dropOffCenter.getBlockZ(), lx = l.getBlockX(), lz = l.getBlockZ();
		if ( lx > cx - 4 && lx < cx + 4
		  && lz > cz - 4 && lz < cz + 4 )
			return true;
			
		// the spawn point for each team is also protected 
		for ( int team=0; team<numTeams.getValue(); team++ )
		{
			Location spawn = getSpawnLocationForTeam(team);
			if ( lx == spawn.getBlockX() && lz == spawn.getBlockZ() && ly < cy + 2 )
				return true;
		}
		return false;
	}
	
	private Location getSpawnLocationForTeam(int team)
	{
		Location loc;
		switch ( team )
		{
			case 0:
				if ( numTeams.getValue() == 3 )
					loc = dropOffCenter.clone().add(-35.5, 0, -20.5); // for 3 teams, ensure they're equidistant from each other, as well as from the plinth
				else
					loc = dropOffCenter.clone().add(-43.5, 0, 0.5);
				loc.setYaw(-90);
				return loc;
			case 1:
				if ( numTeams.getValue() == 3 )
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
		return getSpawnLocationForTeam(indexOfTeam(getTeam(player)));
	}
	
	@Override
	public void gameStarted()
	{
		// don't let drops spawn on the plateau for a couple of seconds
		getScheduler().runTaskLater(getPlugin(), new Runnable() {
			public void run() {
				generating = false;
			}
		}, 60);

		scoresForTypes.clear();
		
		int[] teamCounts = new int[numTeams.getValue()];
		List<Player> players = getOnlinePlayers();
		
		while ( players.size() > 0 )
		{// pick random player, add them to one of the teams with the fewest players (picked randomly)
			Player player = players.remove(random.nextInt(players.size()));
			allocatePlayer(player, teamCounts);
		}
		
		broadcastMessage(ChatColor.YELLOW + "Day 1 of " + dayLimit);
		dayCountProcessID = getScheduler().scheduleSyncRepeatingTask(getPlugin(), new Runnable() {
			long lastRun = 0;
			public void run()
			{
				long time = getPlugin().getServer().getWorlds().get(0).getTime();
				
				if ( time < lastRun ) // time of day has gone backwards: must be a new day! Allocate the killers
				{
					dayCount ++;
					if ( dayCount >= dayLimit.getValue() )
					{
						endGame();
						getScheduler().cancelTask(dayCountProcessID);
					}
					else
					{
						String message = ChatColor.YELLOW + "Day " + (dayCount+1) + " of " + dayLimit;
						if ( announceScores.isEnabled() )
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
		StringBuilder message = new StringBuilder();
		long winningScore = 0;
		ArrayList<FarmTeamInfo> winningTeams = new ArrayList<FarmTeamInfo>();
		for ( int i=0; i<teams.length; i++ )
		{
			FarmTeamInfo team = teams[i];
			message.append("\n" + team.getChatColor() + team.getName() + ": " + ChatColor.RESET + team.getScore() + " points");
			
			// also count the scores to decide the winning teams
			if ( team.getScore() > winningScore )
			{
				winningScore = team.getScore();
				winningTeams.clear();
				winningTeams.add(team);
			}
			else if ( team.getScore() == winningScore )
				winningTeams.add(team);
		}

		if ( winningTeams.size() == teams.length )
			message.append("\n\nThe game was drawn, the scores are equal");
		else
		{
			message.append("\n\nThe ");
			for ( int i=0; i<winningTeams.size(); i++ )
			{
				if ( i==winningTeams.size()-1 )
					message.append(" and ");
				else if ( i > 0 )
					message.append(", ");
				
				FarmTeamInfo team = teams[i];
				message.append(team.getChatColor() + team.getName() + ChatColor.RESET);
			}
			
			message.append(winningTeams.size() == 0 ? " wins!" : " win!");
		}
		return message.toString();
	}
	
	@Override
	public void gameFinished()
	{
		if ( dayCountProcessID != -1 )
		{
			getScheduler().cancelTask(dayCountProcessID);
			dayCountProcessID = -1;
		}
	}
	
	@Override
	public void playerJoinedLate(Player player)
	{
		// put this player onto one of the teams with the fewest survivors
		TeamInfo[] teams = getTeams();
		int[] teamCounts = new int[numTeams.getValue()];
		for ( int i=0; i<teamCounts.length; i++ )
			teamCounts[i] = getOnlinePlayers(new PlayerFilter().team(teams[i])).size();
				
		TeamInfo team = allocatePlayer(player, teamCounts);
		broadcastMessage(new PlayerFilter().exclude(player), player.getName() + " has joined the " + team.getChatColor() + team.getName());
	}

	private TeamInfo allocatePlayer(Player player, int[] teamCounts)
	{
		int teamNum = Helper.getLowestValueIndex(teamCounts);
		TeamInfo team = getTeams()[teamNum];
		
		setTeam(player, team);
		teamCounts[teamNum] ++;
		player.sendMessage("You are on the " + team.getChatColor() + team.getName() + "\n" + ChatColor.RESET + "Use the /team command to send messages to your team only");
		
		equipPlayer(player, team);

		return team;
	}
	
	private void equipPlayer(Player player, TeamInfo team)
	{
		PlayerInventory inv = player.getInventory();
		Color color = team.getArmorColor();
		
		// give them team-dyed armor, and a sword
		ItemStack armor = new ItemStack(Material.LEATHER_CHESTPLATE);
		
		LeatherArmorMeta meta = ((LeatherArmorMeta)armor.getItemMeta());
		meta.setColor(color);
		
		armor.setItemMeta(meta);
		inv.setChestplate(armor);
		
		armor = new ItemStack(Material.LEATHER_CHESTPLATE);
		armor.setItemMeta(meta);
		inv.setLeggings(armor);
		
		armor = new ItemStack(Material.LEATHER_BOOTS);
		armor.setItemMeta(meta);
		inv.setBoots(armor);
		
		inv.addItem(new ItemStack(Material.IRON_SWORD));
	}

	private boolean isAllowedToDrop(Material type)
	{
		return type != Material.LEATHER_CHESTPLATE
			&& type != Material.LEATHER_LEGGINGS
			&& type != Material.LEATHER_BOOTS;
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerRespawn(PlayerRespawnEvent event)
	{
		final Player player = event.getPlayer();
		getScheduler().scheduleSyncDelayedTask(getPlugin(), new Runnable() {
			public void run() {
				equipPlayer(player, getTeam(player));
			}
		});
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerKilled(PlayerDeathEvent event)
	{
		// players don't drop leather armor or iron swords on death
		List<ItemStack> drops = event.getDrops();
		for ( int i=0; i<drops.size(); i++ )
			if ( !isAllowedToDrop(drops.get(i).getType()) )	
				drops.remove(i--);
	}
	
	@Override
	public void playerQuit(OfflinePlayer player) { }
	
	@Override
	public Location getCompassTarget(Player player)
	{
		return null;
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onItemDrop(PlayerDropItemEvent event)
    {
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
		
		FarmTeamInfo team = (FarmTeamInfo)getTeam(event.getPlayer());
		int teamNum = indexOfTeam(team);
		long dropScore = 0;
		
		for ( int i=0; i<stack.getAmount(); i++ )
			dropScore += getScoreForItem(stack.getType(), teamNum);
		
		event.getPlayer().sendMessage(stack.getType().name() + ": score +" + dropScore);
		team.addScore(dropScore);
		
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
		if ( !diminishingReturns.isEnabled() )
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
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void entityDamaged(EntityDamageEvent event)
	{
		if ( friendlyFire.isEnabled() )
			return;
		
		Player victim = (Player)event.getEntity();
		if ( victim == null )
			return;
		
		Player attacker = Helper.getAttacker(event);
		if ( attacker == null )
			return;
		
		if ( getTeam(victim) == getTeam(attacker) )
			event.setCancelled(true);
	}
}
