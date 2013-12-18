package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.wc.SVNEventAction;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventTest {

    @Test
    public void testEventActionIdsAreUnique() throws Exception {
        HashMap<String, List<String>> idToEvents = new HashMap<String, List<String>>();
        HashMap<String, List<String>> nameToEvents = new HashMap<String, List<String>>();

        // Get all class fields
        Field[] fields = SVNEventAction.class.getDeclaredFields();

        for (Field field : fields) {
            if (field.getType() == SVNEventAction.class) {
                final String fieldName = field.getName();
                final SVNEventAction eventAction = (SVNEventAction) field.get(null);

                // IDs mapping
                Field id = eventAction.getClass().getDeclaredField("myID");
                id.setAccessible(true);
                int idValue = id.getInt(eventAction);
                String idValueAsString = Integer.toString(idValue);

                List<String> events = idToEvents.get(idValueAsString);
                if (events == null) {
                    events = new ArrayList<String>();
                    idToEvents.put(idValueAsString, events);
                }
                // Add SVNEventAction constant
                events.add(fieldName);


                // Names mapping
                Field name = eventAction.getClass().getDeclaredField("myName");
                name.setAccessible(true);
                String nameValue = (String) name.get(eventAction);

                events = nameToEvents.get(nameValue);
                if (events == null) {
                    events = new ArrayList<String>();
                    nameToEvents.put(nameValue, events);
                }
                // Add SVNEventAction constant
                events.add(fieldName);
            }
        }

        for (Map.Entry<String, List<String>> entry : idToEvents.entrySet()) {
            if (entry.getValue().size() > 1) {
                Assert.fail("Duplicate ID: "+ entry.getKey() + " >>> " + entry.getValue());
            }
        }

        for (Map.Entry<String, List<String>> entry : nameToEvents.entrySet()) {
            if (entry.getValue().size() > 1) {
                Assert.fail("Duplicate name: "+ entry.getKey() + " >>> " + entry.getValue());
            }
        }
    }
}
