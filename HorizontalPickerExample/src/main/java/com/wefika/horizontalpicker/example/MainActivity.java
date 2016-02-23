/*
 * Copyright 2014 Blaž Šolar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wefika.horizontalpicker.example;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.wefika.horizontalpicker.HorizontalPicker;
import com.wefika.horizontalpicker.HorizontalPicker.OnItemClicked;
import com.wefika.horizontalpicker.HorizontalPicker.OnItemSelected;

public class MainActivity extends Activity implements OnItemSelected, OnItemClicked, OnClickListener {

    private HorizontalPicker picker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        picker = (HorizontalPicker) findViewById(R.id.picker);
        picker.setOnItemClickedListener(this);
        picker.setOnItemSelectedListener(this);

        findViewById(R.id.item_select).setOnClickListener(this);
        findViewById(R.id.toggle_infinite).setOnClickListener(this);
    }

    @Override
    public void onItemSelected(int index)    {
        Toast.makeText(this, "Item selected: " + index, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onItemClicked(int index) {
        Toast.makeText(this, "Item clicked: " + index, Toast.LENGTH_SHORT).show();
    }

    @Override public void onClick(View v) {
        switch (v.getId()) {
            case R.id.item_select:
                picker.setSelectedItem(3);
                break;
            case R.id.toggle_infinite:
                picker.setInfinite(!picker.isInfinite());
                break;
        }
    }
}
