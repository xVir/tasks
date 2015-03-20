package com.todoroo.astrid.voice;

import android.app.Activity;
import android.widget.Toast;

import com.todoroo.andlib.data.Callback;
import com.todoroo.astrid.data.Task;

import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;

import ai.api.AIConfiguration;
import ai.api.model.AIError;
import ai.api.model.AIResponse;
import ai.api.model.Result;
import ai.api.ui.AIDialog;

@Singleton
public class ApiAiTasksAgent {

    private Callback<Task> addTaskCallback;
    private AIDialog aiDialog;

    private final AIConfiguration aiConfiguration = new AIConfiguration("7d34f099a1484de1be4dec9fc75d1d0c",
            "cb9693af-85ce-4fbf-844a-5563722fc27f",
            AIConfiguration.SupportedLanguages.English,
            AIConfiguration.RecognitionEngine.System);

    @Inject
    public ApiAiTasksAgent(final Activity activity) {

        aiDialog = new AIDialog(activity, aiConfiguration);

        aiDialog.setResultsListener(new AIDialog.AIDialogListener() {
            @Override
            public void onResult(AIResponse aiResponse) {
                try {
                    if (!aiResponse.isError()) {
                        Result result = aiResponse.getResult();

                        switch (result.getAction()) {
                            case "task.create":

                                Task newTask = new Task();
                                newTask.setTitle(result.getStringParameter("text"));

                                switch (result.getStringParameter("priority")) {
                                    case "urgent":
                                        newTask.setImportance(Task.IMPORTANCE_DO_OR_DIE);
                                        break;

                                    case "important":
                                        newTask.setImportance(Task.IMPORTANCE_MUST_DO);
                                        break;
                                }

                                if (result.getDateParameter("date") != null) {
                                    newTask.setDueDate(Task.URGENCY_SPECIFIC_DAY, result.getDateParameter("date"));
                                }

                                if (result.getDateTimeParameter("date-time") != null) {
                                    newTask.setDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, result.getDateTimeParameter("date-time"));
                                }

                                if (result.getTimeParameter("time") != null) {
                                    newTask.setDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, result.getTimeParameter("time"));
                                }

                                if (addTaskCallback != null) {
                                    addTaskCallback.apply(newTask);
                                }

                                break;

                            case "task.complete":
                                break;
                        }

                    }

                    aiDialog.close();

                } catch (Exception e) {
                    Toast.makeText(activity, e.toString(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(AIError aiError) {
                Toast.makeText(activity, aiError.toString(), Toast.LENGTH_SHORT).show();
                aiDialog.close();
            }
        });
    }

    public void startRecognition() {
        aiDialog.showAndListen();
    }

    public void setAddTaskCallback(Callback<Task> addTaskCallback) {
        this.addTaskCallback = addTaskCallback;
    }
}
