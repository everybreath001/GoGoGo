package com.zcshou.chengziapk;

import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.zcshou.database.DataBaseFavoriteLocation;
import com.zcshou.utils.GoUtils;

import java.util.ArrayList;
import java.util.List;

public class FavoriteActivity extends BaseActivity {
    private SharedPreferences sharedPreferences;
    private SQLiteDatabase mFavoriteDB;
    private TextView mEmptyText;
    private ListView mGroupList;
    private ListView mPointList;

    private final List<String> mGroups = new ArrayList<>();
    private final List<DataBaseFavoriteLocation.FavoritePoint> mPoints = new ArrayList<>();
    private String mCurrentGroup = DataBaseFavoriteLocation.DEFAULT_GROUP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimary, this.getTheme()));

        setContentView(R.layout.activity_favorite);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            DataBaseFavoriteLocation dbFavorite = new DataBaseFavoriteLocation(getApplicationContext());
            mFavoriteDB = dbFavorite.getWritableDatabase();
        } catch (Exception e) {
            Log.e("FavoriteActivity", "ERROR - initFavoriteDataBase");
        }

        mEmptyText = findViewById(R.id.favorite_point_empty);
        mGroupList = findViewById(R.id.favorite_group_list);
        mPointList = findViewById(R.id.favorite_point_list);

        initGroupList();
        initPointList();

        mCurrentGroup = sharedPreferences.getString("favorite_current_group",
                DataBaseFavoriteLocation.DEFAULT_GROUP);
        updateGroupList();
        updatePointList();
    }

    @Override
    protected void onDestroy() {
        if (mFavoriteDB != null) {
            mFavoriteDB.close();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_favorite, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            this.finish();
            return true;
        } else if (id == R.id.action_add_group) {
            showCreateGroupDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initGroupList() {
        mGroupList.setOnItemClickListener((parent, view, position, id) -> {
            mCurrentGroup = mGroups.get(position);
            sharedPreferences.edit().putString("favorite_current_group", mCurrentGroup).apply();
            updateGroupList();
            updatePointList();
        });

        mGroupList.setOnItemLongClickListener((parent, view, position, id) -> {
            String group = mGroups.get(position);
            PopupMenu popupMenu = new PopupMenu(this, view);
            popupMenu.setGravity(Gravity.END | Gravity.BOTTOM);
            popupMenu.getMenu().add("重命名");
            popupMenu.getMenu().add("删除");

            popupMenu.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if (title.equals("重命名")) {
                    showRenameGroupDialog(group);
                    return true;
                } else if (title.equals("删除")) {
                    showDeleteGroupDialog(group);
                    return true;
                }
                return false;
            });

            popupMenu.show();
            return true;
        });
    }

    private void initPointList() {
        mPointList.setOnItemClickListener((parent, view, position, id) -> {
            DataBaseFavoriteLocation.FavoritePoint point = mPoints.get(position);
            if (!MainActivity.showLocation(point.name,
                    point.longitudeCustom, point.latitudeCustom)) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.history_error_location));
                return;
            }
            GoUtils.DisplayToast(this, getResources().getString(R.string.favorite_jump_ok));
            this.finish();
        });

        mPointList.setOnItemLongClickListener((parent, view, position, id) -> {
            DataBaseFavoriteLocation.FavoritePoint point = mPoints.get(position);
            PopupMenu popupMenu = new PopupMenu(this, view);
            popupMenu.setGravity(Gravity.END | Gravity.BOTTOM);
            popupMenu.getMenu().add("重命名");
            popupMenu.getMenu().add("移动分组");
            popupMenu.getMenu().add("删除");

            popupMenu.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if (title.equals("重命名")) {
                    showRenamePointDialog(point);
                    return true;
                } else if (title.equals("移动分组")) {
                    showMovePointDialog(point);
                    return true;
                } else if (title.equals("删除")) {
                    showDeletePointDialog(point);
                    return true;
                }
                return false;
            });

            popupMenu.show();
            return true;
        });
    }

    private void updateGroupList() {
        mGroups.clear();
        mGroups.addAll(DataBaseFavoriteLocation.getGroups(mFavoriteDB));

        if (!mGroups.contains(mCurrentGroup)) {
            mCurrentGroup = DataBaseFavoriteLocation.DEFAULT_GROUP;
            sharedPreferences.edit().putString("favorite_current_group", mCurrentGroup).apply();
        }

        List<String> items = new ArrayList<>();
        for (String group : mGroups) {
            int count = DataBaseFavoriteLocation.getFavoriteCount(mFavoriteDB, group);
            String label = group + " (" + count + ")";
            items.add(group.equals(mCurrentGroup) ? "● " + label : label);
        }

        mGroupList.setAdapter(new ArrayAdapter<>(this,
                R.layout.favorite_group_item, R.id.favorite_group_item_text, items));
    }

    private void updatePointList() {
        mPoints.clear();
        mPoints.addAll(DataBaseFavoriteLocation.getFavorites(mFavoriteDB, mCurrentGroup));

        mEmptyText.setVisibility(mPoints.isEmpty() ? View.VISIBLE : View.GONE);
        mPointList.setVisibility(mPoints.isEmpty() ? View.GONE : View.VISIBLE);

        List<String> items = new ArrayList<>();
        for (DataBaseFavoriteLocation.FavoritePoint point : mPoints) {
            String name = TextUtils.isEmpty(point.name)
                    ? getResources().getString(R.string.history_location_default_name) : point.name;
            items.add(name + "\n经度:" + point.longitudeCustom + " 纬度:" + point.latitudeCustom);
        }

        mPointList.setAdapter(new ArrayAdapter<>(this,
                R.layout.favorite_point_item, R.id.favorite_point_item_text, items));
    }

    private void showCreateGroupDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.favorite_group_title))
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String group = input.getText().toString().trim();
                    if (TextUtils.isEmpty(group)) {
                        return;
                    }
                    if (mGroups.contains(group)) {
                        GoUtils.DisplayToast(this, getResources().getString(R.string.favorite_group_exists));
                        return;
                    }
                    DataBaseFavoriteLocation.createGroup(mFavoriteDB, group);
                    mCurrentGroup = group;
                    sharedPreferences.edit().putString("favorite_current_group", mCurrentGroup).apply();
                    updateGroupList();
                    updatePointList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRenameGroupDialog(String group) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(group);

        new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.favorite_group_title))
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newGroup = input.getText().toString().trim();
                    if (TextUtils.isEmpty(newGroup) || newGroup.equals(group)) {
                        return;
                    }
                    if (mGroups.contains(newGroup)) {
                        GoUtils.DisplayToast(this, getResources().getString(R.string.favorite_group_exists));
                        return;
                    }
                    DataBaseFavoriteLocation.renameGroup(mFavoriteDB, group, newGroup);
                    if (mCurrentGroup.equals(group)) {
                        mCurrentGroup = newGroup;
                        sharedPreferences.edit().putString("favorite_current_group", mCurrentGroup).apply();
                    }
                    updateGroupList();
                    updatePointList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteGroupDialog(String group) {
        new AlertDialog.Builder(this)
                .setTitle("警告")
                .setMessage(getResources().getString(R.string.favorite_delete_group_tips, group))
                .setPositiveButton("确定", (dialog, which) -> {
                    DataBaseFavoriteLocation.deleteGroup(mFavoriteDB, group);
                    if (mCurrentGroup.equals(group)) {
                        mCurrentGroup = DataBaseFavoriteLocation.DEFAULT_GROUP;
                        sharedPreferences.edit().putString("favorite_current_group", mCurrentGroup).apply();
                    }
                    updateGroupList();
                    updatePointList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRenamePointDialog(DataBaseFavoriteLocation.FavoritePoint point) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(point.name);

        new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.favorite_rename_title))
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    DataBaseFavoriteLocation.updateFavoriteName(mFavoriteDB, point.id,
                            input.getText().toString().trim());
                    updatePointList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMovePointDialog(DataBaseFavoriteLocation.FavoritePoint point) {
        List<String> groups = DataBaseFavoriteLocation.getGroups(mFavoriteDB);
        groups.remove(point.group);

        if (groups.isEmpty()) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.favorite_no_other_group));
            return;
        }

        String[] items = groups.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.favorite_move_group))
                .setItems(items, (dialog, which) -> {
                    DataBaseFavoriteLocation.updateFavoriteGroup(mFavoriteDB, point.id, items[which]);
                    updateGroupList();
                    updatePointList();
                })
                .show();
    }

    private void showDeletePointDialog(DataBaseFavoriteLocation.FavoritePoint point) {
        new AlertDialog.Builder(this)
                .setTitle("警告")
                .setMessage("确定要删除该点位吗?")
                .setPositiveButton("确定", (dialog, which) -> {
                    DataBaseFavoriteLocation.deleteFavorite(mFavoriteDB, point.id);
                    updatePointList();
                    GoUtils.DisplayToast(this, getResources().getString(R.string.favorite_delete_ok));
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
