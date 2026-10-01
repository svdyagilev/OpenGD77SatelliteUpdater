package ru.opengd77.satupdate;

import org.junit.Test;
import static org.junit.Assert.*;

public class CodeplugNavigationTest {
    @Test public void nestedRadioOptionsReturnToSettingsBeforeOverview() {
        for(int option:new int[]{CodeplugNavigation.VOX,14,9}) {
            int page=CodeplugNavigation.parent(option);
            assertEquals(CodeplugNavigation.RADIO_SETTINGS,page);
            page=CodeplugNavigation.parent(page);
            assertEquals(CodeplugNavigation.RADIO,page);
            page=CodeplugNavigation.parent(page);
            assertEquals(CodeplugNavigation.OVERVIEW,page);
            assertEquals(-1,CodeplugNavigation.parent(page));
        }
    }
    @Test public void everyPageReachesOverviewWithoutCycles() {
        checkSubtree(CodeplugNavigation.OVERVIEW,0);
    }
    private void checkSubtree(int page,int depth) {
        assertTrue("Hierarchy cycle or excessive nesting",depth<5);
        for(int child:CodeplugNavigation.children(page)) {
            assertNotEquals(CodeplugNavigation.OVERVIEW,child);
            assertEquals(page,CodeplugNavigation.parent(child));
            assertFalse(CodeplugNavigation.title(child).isEmpty());
            checkSubtree(child,depth+1);
        }
    }
    @Test public void shortcutChannelReturnsToChannelsSection() {
        assertEquals(CodeplugNavigation.CHANNELS,CodeplugNavigation.parent(1));
        assertEquals(CodeplugNavigation.OVERVIEW,CodeplugNavigation.parent(CodeplugNavigation.CHANNELS));
    }
}
