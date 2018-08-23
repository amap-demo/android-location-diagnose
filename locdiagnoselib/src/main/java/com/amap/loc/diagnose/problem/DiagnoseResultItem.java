package com.amap.loc.diagnose.problem;

import java.util.Arrays;
import java.util.List;

/**
 * 检查结果信息
 *
 * 检查无异常时，使用{@link #checkOk()}方法创建，由于无异常时不展示任何额外信息，因此不需要提供任何参数
 *
 * 检查存在异常时，使用{@link #checkError(String, List)}方法创建，此时要展示异常描述及具体的异常原因，
 *
 */
public class DiagnoseResultItem {

    public enum CheckResult {
        Ok, Error, Warning
    }

    /**
     * 检查结果的额外描述信息
     */
    public static class SubItem {

        public SubItem(boolean isOk, String description) {
            this.checkResult = isOk ? CheckResult.Ok : CheckResult.Error;
            this.description = description;
        }

        public SubItem(CheckResult checkResult, String description) {
            this.checkResult = checkResult;
            this.description = description;
        }

        public CheckResult checkResult;
        public String description;

        @Override
        public String toString() {
            return "SubItem{" +
                    "checkResult=" + checkResult +
                    ", description='" + description + '\'' +
                    '}';
        }
    }

    /*package*/ int icon;
    /*package*/ String title;

    public CheckResult checkResult;
    public String errorHint;
    public List<SubItem> subItems;

    public static DiagnoseResultItem checkOk() {
        DiagnoseResultItem resultItem = new DiagnoseResultItem();
        resultItem.checkResult = CheckResult.Ok;
        return resultItem;
    }

    public static DiagnoseResultItem checkError(String errorHint, SubItem... errorItems) {
        return checkError(errorHint, Arrays.asList(errorItems));
    }

    public static DiagnoseResultItem checkError(String errorHint, List<SubItem> errorItems) {
        DiagnoseResultItem resultItem = new DiagnoseResultItem();
        resultItem.checkResult = CheckResult.Error;
        resultItem.errorHint = errorHint;
        resultItem.subItems = errorItems;
        return resultItem;
    }

    @Override
    public String toString() {
        return "DiagnoseResultItem{" +
                "icon=" + icon +
                ", title='" + title + '\'' +
                ", checkResult=" + checkResult +
                ", errorHint='" + errorHint + '\'' +
                ", subItems=" + subItems +
                '}';
    }
}
