package com.amap.loc.diagnose.problem;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.amap.loc.diagnose.R;

import java.util.List;

public class DiagnoseResultView extends FrameLayout {

    private List<DiagnoseResultItem> data;
    private ListView listView;
    private DiagnoseResultListAdapter adapter;

    public DiagnoseResultView(Context context) {
        super(context);
        init();
    }

    public DiagnoseResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.view_diagnose_result, this);

        listView = findViewById(R.id.view_diagnose_result_list);
    }

    public void setData(List<DiagnoseResultItem> data) {
        this.data = data;
        int errorCount = 0;
        for (DiagnoseResultItem i : data) {
            if (i.checkResult == DiagnoseResultItem.CheckResult.Error) {
                errorCount++;
            }
        }
        if (errorCount > 0) {
            ViewGroup listHeaderView = (ViewGroup) LayoutInflater.from(getContext()).inflate(R.layout.view_diagnose_result_header, listView, false);
            listView.addHeaderView(listHeaderView);
            ((TextView)listHeaderView.findViewById(R.id.view_diagnose_result_header_hint)).setText("请按照建议配置手机");
            Spannable ss = new SpannableString(errorCount + "项");
            ss.setSpan(new RelativeSizeSpan(2.5f), 0, ss.length() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ((TextView)listHeaderView.findViewById(R.id.view_diagnose_result_header_desc)).setText(ss);
        }
        adapter = new DiagnoseResultListAdapter(data);
        listView.setAdapter(adapter);
    }

    private static class DiagnoseResultListAdapter extends BaseAdapter {

        private List<DiagnoseResultItem> data;

        private static class ViewHolder {
            ImageView icon;
            ImageView checkOk;
            TextView title;
            TextView desc;
            ViewGroup subItemsContainer;
        }

        DiagnoseResultListAdapter(List<DiagnoseResultItem> data) {
            this.data = data;
        }

        @Override
        public int getCount() {
            return data == null ? 0 : data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_view_diagnose_result, parent, false);
                vh = new ViewHolder();
                vh.icon = convertView.findViewById(R.id.item_view_diagnose_result_icon);
                vh.checkOk = convertView.findViewById(R.id.item_view_diagnose_result_ok);
                vh.title = convertView.findViewById(R.id.item_view_diagnose_result_title);
                vh.desc = convertView.findViewById(R.id.item_view_diagnose_result_desc);
                vh.subItemsContainer = convertView.findViewById(R.id.item_view_diagnose_result_sub);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            DiagnoseResultItem item = data.get(position);
            vh.title.setText(item.title);
            vh.icon.setImageResource(item.icon);
            if (item.checkResult == DiagnoseResultItem.CheckResult.Ok) {
                vh.desc.setTextColor(0xff666666);
                vh.desc.setText("无异常");
                vh.checkOk.setImageResource(R.drawable.ok);
            } else if (item.checkResult == DiagnoseResultItem.CheckResult.Error) {
                vh.desc.setTextColor(0xffec5757);
                vh.desc.setText(item.errorHint);
                vh.checkOk.setImageResource(R.drawable.error);
            } else if (item.checkResult == DiagnoseResultItem.CheckResult.Warning) {
                vh.desc.setTextColor(0xff666666);
                vh.desc.setText(item.errorHint);
                vh.checkOk.setImageResource(R.drawable.warning);
            }
            if (item.checkResult != DiagnoseResultItem.CheckResult.Ok && item.subItems != null && item.subItems.size() > 0) {
                vh.subItemsContainer.setVisibility(VISIBLE);
                vh.subItemsContainer.removeAllViews();
                for (DiagnoseResultItem.SubItem subItem : item.subItems) {
                    if (subItem.checkResult != DiagnoseResultItem.CheckResult.Ok) {
                        ViewGroup subItemView = (ViewGroup) LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_view_diagnose_result_sub, vh.subItemsContainer, false);
//                        ((TextView) subItemView.findViewById(R.id.item_view_diagnose_result_sub_title)).setText(subItem.title);
                        ((TextView) subItemView.findViewById(R.id.item_view_diagnose_result_sub_desc)).setText(subItem.description);
                        vh.subItemsContainer.addView(subItemView);
                    }
                }
            } else {
                vh.subItemsContainer.setVisibility(GONE);
            }
            return convertView;
        }
    }

}
