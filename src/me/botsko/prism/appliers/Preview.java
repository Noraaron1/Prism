package me.botsko.prism.appliers;

import java.util.ArrayList;
import java.util.List;

import me.botsko.prism.Prism;
import me.botsko.prism.actionlibs.ActionsQuery;
import me.botsko.prism.actionlibs.QueryParameters;
import me.botsko.prism.actions.Action;
import me.botsko.prism.actions.BlockAction;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class Preview {
	
	/**
	 * 
	 */
	private Prism plugin;
	
	/**
	 * 
	 */
	private Player player;
	
	/**
	 * 
	 */
	private List<Action> results;
	
	
	/**
	 * 
	 * @param plugin
	 * @return 
	 */
	public Preview( Prism plugin, Player player, List<Action> results ) {
		this.plugin = plugin;
		this.player = player;
		this.results = results;
	}
	
	
	/**
	 * 
	 */
	public void preview( QueryParameters parameters ){
		
		if(!results.isEmpty()){
			
			ArrayList<Undo> undo = new ArrayList<Undo>();
			
			int rolled_back_count = 0;
			
			for(Action a : results){
				
				World world = plugin.getServer().getWorld(a.getWorld_name());
				
				//Get some data from the entry
				Location loc = new Location(world, a.getX(), a.getY(), a.getZ());
				
				/**
				 * Rollback block changes
				 */
				if( a instanceof BlockAction ){

					BlockAction b = (BlockAction) a;
					Block block = world.getBlockAt(loc);
					
					// Record the change temporarily so we can cancel
					// and update the client
					Undo u = new Undo( block );
					undo.add(u);
					
					
					// If the block was placed, we need to remove it
					// @todo it may not always be air that was replaced. we should log that
					if(a.getType().doesCreateBlock()){
						// @todo ensure we're not removing a new block that's been placed by someone else
						if(!block.getType().equals(Material.AIR)){
							player.sendBlockChange(block.getLocation(), Material.AIR, (byte)0);
							rolled_back_count++;
						}
					} else {
						
						/**
						 * Restore the block that was removed, unless something
						 * other than air occupies the spot.
						 */
						if(block.getType().equals(Material.AIR)){
							player.sendBlockChange(block.getLocation(), b.getBlock_id(), b.getBlock_subid());
							rolled_back_count++;
						}
					}
				}
				
				PreviewSession ps = new PreviewSession( player, undo, parameters );
				
				// Append the preview and blocks temporarily
				plugin.playerActivePreviews.put(player.getName(), ps);
				
			}
			
			player.sendMessage( plugin.playerHeaderMsg( rolled_back_count + " planned reversals." + ChatColor.GRAY + " Use /prism preview apply to confirm this rollback." ) );
			
		} else {
			player.sendMessage( plugin.playerError( "Nothing found to preview. Try using /prism l (args) first." ) );
		}
	}
	
	
	/**
	 * 
	 */
	public void cancel_preview(){
		if(plugin.playerActivePreviews.containsKey(player.getName())){
			
			PreviewSession previewSession = plugin.playerActivePreviews.get( player.getName() );
			
			if(!previewSession.getUndo_queue().isEmpty()){
				for(Undo u : previewSession.getUndo_queue()){
					player.sendBlockChange(u.getOriginalBlock().getLocation(), u.getOriginalBlock().getTypeId(), u.getOriginalBlock().getData());
				}
			}
			
			player.sendMessage( plugin.playerHeaderMsg( "Preview canceled." + ChatColor.GRAY + " Please come again!" ) );
			
			plugin.playerActivePreviews.remove( player.getName() );
			
		}
	}
	
	
	/**
	 * 
	 */
	public void apply_preview(){
		if(plugin.playerActivePreviews.containsKey(player.getName())){
			
			// Get preview session
			PreviewSession ps = plugin.playerActivePreviews.get(player.getName());
			
			// Forward as a rollback
			ActionsQuery aq = new ActionsQuery(plugin);
			List<Action> results = aq.lookup( ps.getArgs() );
			if(!results.isEmpty()){
				
				player.sendMessage( plugin.playerHeaderMsg("Applying rollback from preview...") );
				Rollback rb = new Rollback( plugin, player, results );
				rb.rollback();
				
			} else {
				// @todo no results
			}
			
			plugin.playerActivePreviews.remove( player.getName() );
			
		}
	}
}