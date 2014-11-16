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
import android.widget.Toast;

import com.wefika.horizontalpicker.HorizontalPicker;

public class MainActivity extends Activity implements HorizontalPicker.OnItemSelected, HorizontalPicker.OnItemClicked {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        HorizontalPicker picker = (HorizontalPicker) findViewById(R.id.picker);
        picker.setOnItemClickedListener(this);
        picker.setOnItemSelectedListener(this);
    }

    @Override
    public void onItemSelected(int index)    {
        Toast.makeText(this, "Item selected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onItemClicked(int index) {
        Toast.makeText(this, "Item clicked", Toast.LENGTH_SHORT).show();
    }
}
