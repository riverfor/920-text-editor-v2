/*
 * Copyright (C) 2016 Jecelyin Peng <jecelyin@gmail.com>
 *
 * This file is part of 920 Text Editor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jecelyin.android.file_explorer;

import android.app.Activity;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;

import com.afollestad.materialdialogs.MaterialDialog;
import com.jecelyin.android.file_explorer.adapter.FileListPagerAdapter;
import com.jecelyin.android.file_explorer.databinding.FileExplorerActivityBinding;
import com.jecelyin.android.file_explorer.io.JecFile;
import com.jecelyin.android.file_explorer.io.LocalFile;
import com.jecelyin.common.app.JecActivity;
import com.jecelyin.common.utils.IOUtils;
import com.jecelyin.common.utils.UIUtils;
import com.jecelyin.editor2.Pref;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
public class FileExplorerActivity extends JecActivity implements View.OnClickListener {
    private FileExplorerActivityBinding binding;
    private FileListPagerAdapter adapter;
    private static final int MODE_PICK_FILE = 1;
    private static final int MODE_PICK_PATH = 2;
    private int mode;
    private String fileEncoding = null;
    private String lastPath;

    public static void startPickFileActivity(Activity activity, String destFile, int requestCode) {
        Intent it = new Intent(activity, FileExplorerActivity.class);
        it.putExtra("mode", MODE_PICK_FILE);
        it.putExtra("dest_file", destFile);
        activity.startActivityForResult(it, requestCode);
    }

    public static void startPickPathActivity(Activity activity, String destFile, String encoding, int requestCode) {
        Intent it = new Intent(activity, FileExplorerActivity.class);
        it.putExtra("mode", MODE_PICK_PATH);
        it.putExtra("dest_file", destFile);
        it.putExtra("encoding", encoding);
        activity.startActivityForResult(it, requestCode);
    }

    @NonNull
    public static String getFile(Intent it) {
        return it.getStringExtra("file");
    }

    /**
     *
     * @param it
     * @return null时为自动检测
     */
    @Nullable
    public static String getFileEncoding(Intent it) {
        return it.getStringExtra("encoding");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.file_explorer_activity);

        Intent it = getIntent();
        mode = it.getIntExtra("mode", MODE_PICK_FILE);

        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(mode == MODE_PICK_FILE ? R.string.open_file : R.string.save_file);

        lastPath = Pref.getInstance(this).getLastOpenPath();
        if(TextUtils.isEmpty(lastPath)) {
            lastPath = Environment.getExternalStorageDirectory().getPath();
        }

        String destPath = it.getStringExtra("dest_file");

        if(!TextUtils.isEmpty(destPath)) {
            File dest = new File(destPath);
            lastPath = dest.isFile() ? dest.getParent() : dest.getPath();
            binding.filenameEditText.setText(dest.getName());
        }

        initPager();
        binding.saveBtn.setOnClickListener(this);
        binding.fileEncodingTextView.setOnClickListener(this);

        String encoding = it.getStringExtra("encoding");
        fileEncoding = encoding;
        if(TextUtils.isEmpty(encoding)) {
            encoding = getString(R.string.auto_detection_encoding);
        }
        binding.fileEncodingTextView.setText(encoding);

        binding.filenameLayout.setVisibility(mode == MODE_PICK_FILE ? View.GONE : View.VISIBLE);

    }

    private void initPager() {
        adapter = new FileListPagerAdapter(getSupportFragmentManager());

        JecFile path = new LocalFile(lastPath);
        adapter.addPath(path);
        binding.viewPager.setAdapter(adapter);
    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        if(item.getItemId() == android.R.id.home) {
//            FileListPagerFragment fragment = adapter.getCurrentFragment();
//            if(fragment != null && fragment.onBackPressed()) {
//                return true;
//            }
//        }
//        return super.onOptionsItemSelected(item);
//    }

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        if(keyCode == KeyEvent.KEYCODE_BACK) {
//            FileListPagerFragment fragment = adapter.getCurrentFragment();
//            if(fragment != null && fragment.onBackPressed()) {
//                return true;
//            }
//        }
//        return super.onKeyDown(keyCode, event);
//    }

    @Override
    protected void onPause() {
        super.onPause();

        Pref.getInstance(this).setLastOpenPath(lastPath);
    }

    boolean onSelectFile(JecFile file) {
        if(file.isFile()) {
            if (mode == MODE_PICK_FILE) {
                Intent it = new Intent();
                it.putExtra("file", file.getPath());
                it.putExtra("encoding", fileEncoding);
                setResult(RESULT_OK, it);
                finish();
            } else {
                binding.filenameEditText.setText(file.getName());
            }

            return true;
        } else if(file.isDirectory()) {
            lastPath = file.getPath();
        }

        return false;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == R.id.save_btn) {
            onSave();
        } else if(id == R.id.file_encoding_textView) {
            onShowEncodingList();
        }
    }

    private void onShowEncodingList() {
        SortedMap m = Charset.availableCharsets();
        Set k = m.keySet();

        int selected = 0;
        String[] names = new String[m.size() + 1];
        names[0] = getString(R.string.auto_detection_encoding);
        Iterator iterator = k.iterator();
        int i = 1;
        while (iterator.hasNext()) {
            String n = (String) iterator.next();
            if(n.equals(fileEncoding))
                selected = i;
            names[i++] = n;
        }

        new MaterialDialog.Builder(this)
                .items(names)
                .itemsCallbackSingleChoice(selected, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                        binding.fileEncodingTextView.setText(charSequence);
                        if(i > 0)
                            fileEncoding = charSequence.toString();
                        return true;
                    }
                })
                .show();
    }

    private void onSave() {
        String fileName = binding.filenameEditText.getText().toString().trim();
        if(TextUtils.isEmpty(fileName)) {
            binding.filenameEditText.setError(getString(R.string.can_not_be_empty));
            return;
        }
        if(IOUtils.isInvalidFilename(fileName)) {
            binding.filenameEditText.setError(getString(R.string.illegal_filename));
            return;
        }
        if(TextUtils.isEmpty(lastPath)) {
            binding.filenameEditText.setError(getString(R.string.unknown_path));
            return;
        }

        File f = new File(lastPath);
        if(f.isFile()) {
            f = f.getParentFile();
        }

        final File newFile = new File(f, fileName);
        if (newFile.exists()) {
            UIUtils.showConfirmDialog(getContext(), getString(R.string.override_file_prompt, fileName), new UIUtils.OnClickCallback() {
                @Override
                public void onOkClick() {
                    saveAndFinish(newFile);
                }
            });
        } else {
            saveAndFinish(newFile);
        }
    }

    private void saveAndFinish(File file) {
        Intent it = new Intent();
        it.putExtra("file", file.getPath());
        it.putExtra("encoding", fileEncoding);
        setResult(RESULT_OK, it);
        finish();
    }
}
