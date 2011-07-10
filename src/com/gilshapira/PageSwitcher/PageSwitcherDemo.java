package com.gilshapira.PageSwitcher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.gilshapira.PageSwitcher.PageSwitcher.Entry;
import com.gilshapira.PageSwitcher.PageSwitcher.SimpleEntry;

/**
 * A demo activity that shows a PageSwitcher at the bottom of the screen. 
 *
 * @author Gil Shapira
 */
public class PageSwitcherDemo extends Activity {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        PageSwitcher switcher = (PageSwitcher) findViewById(R.id.switcher);
        
        // add 5 entries with random preview views
        for (int i = 0; i < 10; ++i) {
            View preview = generatePreview();
            Entry entry = new SimpleEntry(preview);
            switcher.addEntry(entry);
        }
    }
    
    /**
     * @return a random view to use as an entry preview.
     */
    private View generatePreview() {
        TextView textView = new TextView(this);
        textView.setTextColor(Color.LTGRAY);
        textView.setText("Preview " + textView.toString());
        return textView;
    }
    
}