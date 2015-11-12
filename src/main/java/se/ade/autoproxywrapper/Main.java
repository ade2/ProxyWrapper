package se.ade.autoproxywrapper;

import java.io.IOException;
import java.util.concurrent.*;

import com.google.common.eventbus.Subscribe;
import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import se.ade.autoproxywrapper.config.Config;
import se.ade.autoproxywrapper.events.*;
import se.ade.autoproxywrapper.gui.SystemTrayIcon;
import se.ade.autoproxywrapper.gui.controller.MenuController;
import se.ade.autoproxywrapper.statistics.StatisticsTask;

public class Main extends Application {

	private ExecutorService pool = Executors.newSingleThreadExecutor();
	private ScheduledExecutorService schedule = Executors.newSingleThreadScheduledExecutor();

	private Stage primaryStage;
	private MiniHttpProxy proxy;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) {
		try {
			Platform.setImplicitExit(false);
			EventBus.get().register(this);
			this.primaryStage = primaryStage;
			primaryStage.getIcons().add(new Image("/icon/icon512.png"));
			primaryStage.setTitle(Labels.get("app.name"));
			loadMain();
			loadLogView();
			if(!Config.get().isStartMinimized()) {
				primaryStage.show();
			}

			EventBus.get().post(GenericLogEvent.info("Starting " + Labels.get("app.name") + "..."));

			proxy = new MiniHttpProxy();
			pool.submit(proxy);
			schedule.scheduleAtFixedRate(new StatisticsTask(proxy), 10, 10, TimeUnit.MINUTES);
			new SystemTrayIcon(this);
		} catch (Exception e) {
			e.printStackTrace();
			closeApplication();
		}
	}

	private void loadMain() throws IOException {
		FXMLLoader loader = new FXMLLoader();
		loader.setLocation(getClass().getClassLoader().getResource("view/main.fxml"));
		BorderPane pane = loader.load();

		Scene scene = new Scene(pane);
		primaryStage.setScene(scene);
		primaryStage.setOnCloseRequest(event -> primaryStage.hide());
		primaryStage.setOnHiding(event -> primaryStage.hide());
		primaryStage.setOnShown(event -> EventBus.get().post(new ApplicationShowedEvent()));

		loader.<MenuController>getController().setMain(this);
	}

	private void loadLogView() throws IOException {
		FXMLLoader loader = new FXMLLoader();
		loader.setLocation(getClass().getClassLoader().getResource("view/log.fxml"));

		BorderPane pane = (BorderPane) primaryStage.getScene().getRoot();
		pane.setCenter(loader.load());
	}

	public void closeApplication() {
		Platform.exit();
		primaryStage.close();
		schedule.shutdownNow();
		pool.shutdownNow();
		try {
			pool.awaitTermination(3, TimeUnit.SECONDS);
			schedule.awaitTermination(3, TimeUnit.SECONDS);
			System.exit(0);
		} catch (InterruptedException e) {
			System.exit(1);
		}
	}

	public Stage getPrimaryStage() {
		return primaryStage;
	}

	@Subscribe
	public void setModeEvent(SetModeEvent event) {
		if(event.mode == ProxyMode.DISABLED) {
			Config.get().setEnabled(false);

		} else if(event.mode == ProxyMode.AUTO) {
			Config.get().setEnabled(true);
		}
		Config.save();
		EventBus.get().post(GenericLogEvent.info("Restarting..."));
		EventBus.get().post(new RestartEvent());
	}

	@Subscribe
	public void shutdownEvent(ShutDownEvent event) {
		closeApplication();
	}
}
