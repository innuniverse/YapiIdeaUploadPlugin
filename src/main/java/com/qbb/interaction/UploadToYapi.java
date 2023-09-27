package com.qbb.interaction;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.qbb.builder.BuildJsonForDubbo;
import com.qbb.builder.BuildJsonForYapi;
import com.qbb.component.ConfigPersistence;
import com.qbb.constant.ProjectTypeConstant;
import com.qbb.constant.YapiConstant;
import com.qbb.dto.*;
import com.qbb.upload.UploadYapi;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @description: 入口
 * @author: chengsheng@qbb6.com
 * @date: 2019/5/15
 */
public class UploadToYapi extends AnAction {

    private static NotificationGroup notificationGroup;

    static {
        notificationGroup = new NotificationGroup("Java2Json.NotificationGroup", NotificationDisplayType.BALLOON, true);
    }


    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = (Editor) e.getDataContext().getData(CommonDataKeys.EDITOR);

        Project project = editor.getProject();
        String projectToken = null;
        String projectId = null;
        String yapiUrl = null;
        String projectType = null;
        String returnClass = null;
        String attachUpload = null;

//        // 获取配置,idea配置
//        try {
//            final java.util.List<ConfigDTO> configs = ServiceManager.getService(ConfigPersistence.class).getConfigs();
//            if(configs == null || configs.size() == 0){
//                Messages.showErrorDialog("请先去配置界面配置yapi配置","获取配置失败！");
//                return;
//            }
//            PsiFile psiFile = e.getDataContext().getData(CommonDataKeys.PSI_FILE);
//            String virtualFile = psiFile.getVirtualFile().getPath();
//            final List<ConfigDTO> collect = configs.stream()
//                    .filter(it -> {
//                        if (!it.getProjectName().equals(project.getName())) {
//                            return false;
//                        }
//                        final String str = (File.separator + it.getProjectName() + File.separator) + (it.getModuleName().equals(it.getProjectName()) ? "" : (it.getModuleName() + File.separator));
//                        return virtualFile.contains(str);
//                    }).collect(Collectors.toList());
//            if (collect.isEmpty()) {
//                Messages.showErrorDialog("没有找到对应的yapi配置，请在菜单 > Preferences > Other setting > YapiUpload 添加", "Error");
//                return;
//            }
//            final ConfigDTO configDTO = collect.get(0);
//            projectToken = configDTO.getProjectToken();
//            projectId = configDTO.getProjectId();
//            yapiUrl = configDTO.getYapiUrl();
//            projectType = configDTO.getProjectType();
//        } catch (Exception e2) {
//            Messages.showErrorDialog("获取配置失败，异常:  " + e2.getMessage(),"获取配置失败！");
//            return;
//        }

        // 获取配置
        try {
            String projectConfig = new String(editor.getProject().getProjectFile().contentsToByteArray(), "utf-8");
            String[] modules = projectConfig.split("moduleList\">");
            if (modules.length > 1) {
                String[] moduleList = modules[1].split("</")[0].split(",");
                PsiFile psiFile = (PsiFile) e.getDataContext().getData(CommonDataKeys.PSI_FILE);
                String virtualFile = psiFile.getVirtualFile().getPath();
                for (int i = 0; i < moduleList.length; i++) {
                    if (virtualFile.contains(moduleList[i])) {
                        projectToken = projectConfig.split(moduleList[i] + "\\.projectToken\">")[1].split("</")[0];
                        projectId = projectConfig.split(moduleList[i] + "\\.projectId\">")[1].split("</")[0];
                        yapiUrl = projectConfig.split(moduleList[i] + "\\.yapiUrl\">")[1].split("</")[0];
                        projectType = projectConfig.split(moduleList[i] + "\\.projectType\">")[1].split("</")[0];
                        if (projectConfig.split(moduleList[i] + "\\.returnClass\">").length > 1) {
                            returnClass = projectConfig.split(moduleList[i] + "\\.returnClass\">")[1].split("</")[0];
                        }
                        String[] attachs = projectConfig.split(moduleList[i] + "\\.attachUploadUrl\">");
                        if (attachs.length > 1) {
                            attachUpload = attachs[1].split("</")[0];
                        }
                        break;
                    }
                }
            } else {
                projectToken = projectConfig.split("projectToken\">")[1].split("</")[0];
                projectId = projectConfig.split("projectId\">")[1].split("</")[0];
                yapiUrl = projectConfig.split("yapiUrl\">")[1].split("</")[0];
                projectType = projectConfig.split("projectType\">")[1].split("</")[0];
                if (projectConfig.split("returnClass\">").length > 1) {
                    returnClass = projectConfig.split("returnClass\">")[1].split("</")[0];
                }

                String[] attachs = projectConfig.split("attachUploadUrl\">");
                if (attachs.length > 1) {
                    attachUpload = attachs[1].split("</")[0];
                }
            }
        } catch (Exception e2) {
            Notification error = notificationGroup.createNotification("get config error:" + e2.getMessage(), NotificationType.ERROR);
            Notifications.Bus.notify(error, project);
            return;
        }

        // 判断项目类型
        UploadYapi uploadYapi = new UploadYapi();
        if (ProjectTypeConstant.dubbo.equals(projectType)) {
            // 获得dubbo需上传的接口列表 参数对象
            ArrayList<YapiDubboDTO> yapiDubboDTOs = new BuildJsonForDubbo().actionPerformedList(e);
            if (yapiDubboDTOs != null) {
                for (YapiDubboDTO yapiDubboDTO : yapiDubboDTOs) {
                    YapiSaveParam yapiSaveParam = new YapiSaveParam(projectToken, yapiDubboDTO.getTitle(), yapiDubboDTO.getPath(), yapiDubboDTO.getParams(), yapiDubboDTO.getResponse(), Integer.valueOf(projectId), yapiUrl, yapiDubboDTO.getDesc());
                    yapiSaveParam.setStatus(yapiDubboDTO.getStatus());
                    if (!Strings.isNullOrEmpty(yapiDubboDTO.getMenu())) {
                        yapiSaveParam.setMenu(yapiDubboDTO.getMenu());
                    } else {
                        yapiSaveParam.setMenu(YapiConstant.menu);
                    }
                    try {
                        // 上传
                        YapiResponse yapiResponse = uploadYapi.uploadSave(yapiSaveParam, null, project.getBasePath());
                        if (yapiResponse.getErrcode() != 0) {
                            Notification info = notificationGroup.createNotification("上传失败，原因:  " + yapiResponse.getErrmsg(), NotificationType.ERROR);
                            Notifications.Bus.notify(info, project);
                        } else {
                            String url = yapiUrl + "/project/" + projectId + "/interface/api/cat_" + yapiResponse.getCatId();
                            this.setClipboard(url);
                            Notification info = notificationGroup.createNotification("上传成功！接口文档url地址:  " + url, NotificationType.INFORMATION);
                            Notifications.Bus.notify(info, project);
                        }
                    } catch (Exception e1) {
                        Notification info = notificationGroup.createNotification("上传失败！异常:  " + e1, NotificationType.ERROR);
                        Notifications.Bus.notify(info, project);
                    }
                }
            }
        } else if (ProjectTypeConstant.api.equals(projectType)) {
            //获得api 需上传的接口列表 参数对象
            ArrayList<YapiApiDTO> yapiApiDTOS = new BuildJsonForYapi().actionPerformedList(e, attachUpload, returnClass);
            if (yapiApiDTOS != null) {
                for (YapiApiDTO yapiApiDTO : yapiApiDTOS) {
                    YapiSaveParam yapiSaveParam = new YapiSaveParam(projectToken, yapiApiDTO.getTitle(), yapiApiDTO.getPath(), yapiApiDTO.getParams(), yapiApiDTO.getRequestBody(), yapiApiDTO.getResponse(), Integer.valueOf(projectId), yapiUrl, true, yapiApiDTO.getMethod(), yapiApiDTO.getDesc(), yapiApiDTO.getHeader());
                    yapiSaveParam.setReq_body_form(yapiApiDTO.getReq_body_form());
                    yapiSaveParam.setReq_body_type(yapiApiDTO.getReq_body_type());
                    yapiSaveParam.setReq_params(yapiApiDTO.getReq_params());
                    yapiSaveParam.setStatus(yapiApiDTO.getStatus());
                    if (!Strings.isNullOrEmpty(yapiApiDTO.getMenu())) {
                        yapiSaveParam.setMenu(yapiApiDTO.getMenu());
                    } else {
                        yapiSaveParam.setMenu(YapiConstant.menu);
                    }
                    yapiSaveParam.setTags(yapiApiDTO.getTags());
                    try {
                        // 上传
                        YapiResponse yapiResponse = uploadYapi.uploadSave(yapiSaveParam, attachUpload, project.getBasePath());
                        if (yapiResponse.getErrcode() != 0) {
                            Notification info = notificationGroup.createNotification("上传失败，原因:  " + yapiResponse.getErrmsg(), NotificationType.ERROR);
                            Notifications.Bus.notify(info, project);
                        } else {
                            String url = yapiUrl + "/project/" + projectId + "/interface/api/cat_" + yapiResponse.getCatId();
                            this.setClipboard(url);
                            Notification info = notificationGroup.createNotification("上传成功！接口文档url地址:  " + url + yapiResponse.getErrmsg() + "\n上传参数：" + new Gson().toJson(yapiSaveParam), NotificationType.INFORMATION);
                            Notifications.Bus.notify(info, project);
                        }
                    } catch (Exception e1) {
                        Notification info = notificationGroup.createNotification("上传失败！异常:  " + e1, NotificationType.ERROR);
                        Notifications.Bus.notify(info, project);
                    }
                }
            }
        }
    }

    /**
     * @description: 设置到剪切板
     * @param: [content]
     * @return: void
     * @author: chengsheng@qbb6.com
     * @date: 2019/7/3
     */
    private void setClipboard(String content) {
        //获取系统剪切板
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        //构建String数据类型
        StringSelection selection = new StringSelection(content);
        //添加文本到系统剪切板
        clipboard.setContents(selection, null);
    }
}
