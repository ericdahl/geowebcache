/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Marius Suta / The Open Planning Project 2008 
 */
package org.geowebcache.rest;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.wms.BBOX;

public class SeedTask extends GWCTask {
    private static Log log = LogFactory.getLog(org.geowebcache.rest.SeedTask.class);

    private final SeedRequest req; 
    
    private final TileLayer tl;
    
    private boolean reseed = false; 
    
    /**
     * Constructs a SeedTask from a SeedRequest
     * @param req - the SeedRequest
     */
    public SeedTask(SeedRequest req, TileLayer tl, boolean reseed) {
        this.req = req;
        this.tl = tl;
        this.reseed = reseed;
    }

    /**
     * Method doAction().
     * this is where all the actual work is being done to seed a tile layer. 
     */
    void doAction() throws GeoWebCacheException {
        //try {
            //approximate thread creation time
            long START_TIME = System.currentTimeMillis();
            
            tl.isInitialized();
            
            log.info("Begin seeding layer : " + tl.getName());
            int zoomStart = req.getZoomStart().intValue();
            int zoomStop = req.getZoomStop().intValue();
            MimeType mimeType = null;
            try {
                mimeType = MimeType.createFromFormat(req.getMimeFormat());
            } catch (MimeException e4) {
                e4.printStackTrace();
            }
            SRS srs = req.getSRS();
            BBOX bounds = req.getBounds();

            int[][] coveredGridLevels = tl.getCoveredGridLevels(srs,bounds);
            int[] metaTilingFactors = tl.getMetaTilingFactors();
            int tilesPerMetaTile = metaTilingFactors[0] * metaTilingFactors[1];
            
            int arrayIndex = getCurrentThreadArrayIndex();
            int TOTAL_TILES = tileCount(coveredGridLevels, zoomStart, zoomStop); 
            int count = 0;
            
            for (int level = zoomStart; level <= zoomStop; level++) {
                int[] gridBounds = coveredGridLevels[level];
                for (int gridy = gridBounds[1]; gridy <= gridBounds[3];) {

                    for (int gridx = gridBounds[0]; gridx <= gridBounds[2];) {
                        
                        int[] gridLoc = { gridx, gridy, level };

                        Tile tile = new Tile(tl, srs, gridLoc, mimeType,
                                null, null);
                        try {
                            tl.getResponse(tile);
                        } catch (GeoWebCacheException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        // Next column
                        gridx += metaTilingFactors[0] * (1 + threadOffset);
                        
                        count += tilesPerMetaTile;
                        
                        int[][] list = SeedResource.getStatusList();
                        synchronized(list) { 
                            list[arrayIndex]= getStatusInfo(arrayIndex, tl, count, (TOTAL_TILES / threadCount), START_TIME);
                        }
                    }
                    
                    System.out.println("Thread with offset " + threadOffset + " completed row.");
                    // Next row
                    gridy += metaTilingFactors[1];
                }

                log.info("Completed seeding level " + level + " for layer "
                        + tl.getName());
            }
            log.info("Completed seeding layer " + tl.getName());
            int[][] list = SeedResource.getStatusList();
            synchronized(list) {                
                    list[arrayIndex] = new int[3];
            }
        //} catch (Exception e) {
        //    log.error(e.getMessage());
        //    e.printStackTrace();
        //
        //}
    }

    /**
     * helper for counting the number of tiles
     * @param layer
     * @param level
     * @param gridBounds
     * @return
     */
    private int tileCount(int[][] coveredGridLevels, int startZoom, int stopZoom) {
        int count = 0;
        
        for(int i=startZoom; i<=stopZoom; i++) {
            int[] gridBounds = coveredGridLevels[i];
            count += (gridBounds[2] - gridBounds[0] + 1) * (gridBounds[3] - gridBounds[1] + 1);
        }
        
        return count;
    }
    /**
     * Helper method to get an index into the status array for the current thread.
     * Assumes the default name for the threads in the threadpool, i.e. "pool-#-thread-#"
     * where # is an integer. The index in the array will be the number of the thread, 
     * i.e. # in thread-# minus 1, since arrays are zero indexed an thread counting begins at 1.
     * @return
     */
    private int getCurrentThreadArrayIndex() {
        String tn = Thread.currentThread().getName();
        int indexOfnumber = tn.indexOf('d')+2;
        String tmp = tn.substring(indexOfnumber);
        int arrayIndex = Integer.parseInt(tmp);        
        arrayIndex--;
        
        return arrayIndex;
    }
    
    /**
     * Helper method to report status of thread progress.
     * @param arrayIndex
     * @param layer
     * @param zoomStart
     * @param zoomStop
     * @param level
     * @param gridBounds
     * @return
     */
    private int[] getStatusInfo(int arrayIndex, TileLayer layer,
            int tilesCount, int tilesTotal, long start_time) {
        int[] temp = new int[3];
        //working on tile
        temp[0] = tilesCount;
        //out of
        temp[1] = tilesTotal;
        
        //estimated time of completion in seconds, use a moving average over the last 
        long timeSpent = (System.currentTimeMillis() - start_time) / 1000;
        
        long timeTotal = Math.round(timeSpent * ((double) tilesTotal / (double) tilesCount));
        
        int timeRemaining = (int) (timeTotal - timeSpent);
        
        temp[2] = timeRemaining;
        
        return temp; 
    }
}
