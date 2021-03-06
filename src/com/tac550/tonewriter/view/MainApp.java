package com.tac550.tonewriter.view;

import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;

import com.tac550.tonewriter.io.LilyPondWriter;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainApp extends Application {

	public static final String APP_NAME = "ToneWriter";
	public static final String APP_VERSION = "0.3";
	public static final String OS_NAME = System.getProperty("os.name").toLowerCase();

	public static final boolean developerMode = "true".equalsIgnoreCase(System.getProperty("developerMode"));

	// Splash screen stuff
	private static final Effect frostEffect =
			new BoxBlur(10, 10, 3);
	private static final Stage splashStage = new Stage();
	private static final ImageView splashBackground = new ImageView();
	private static final StackPane splashPane = new StackPane();

	// Preferences object and key strings.
	public static Preferences prefs;
	public static final String PREFS_LILYPOND_LOCATION = "LilyPond-Bin-Location";
	public static final String PREFS_THOU_THY_ENABLED = "Thou-Thy-Enabled";
	public static final String PREFS_SAVE_LILYPOND_FILE = "Save-LP-File";

	// The colors that each chord group will take. The maximum number of chord groups is determined by the length of this array.
	public static final Color[] CHORDCOLORS = new Color[]{Color.DARKGREEN, Color.BROWN, Color.BLUEVIOLET, Color.DEEPSKYBLUE, Color.CADETBLUE, Color.BURLYWOOD, Color.GOLD};

	// How tall to make note buttons in the verse view.
	public static final int NOTEBUTTONHEIGHT = 15;

	// LilyPond connection stuff.
	private static boolean lilyPondAvailable = false;
	private static File lilyPondDirectory;

	// Fields for main stage and controller.
	private Stage mainStage;
	private MainSceneController mainController;

	public static void main(String[] args) {

		System.out.println("Developer mode: " + (developerMode ? "enabled" : "disabled"));

		launch(args);
	}

	@Override
	public void start(Stage main_stage) {
		// Set up preferences system
		prefs = Preferences.userNodeForPackage(this.getClass());

		// Print saved LilyPond directory if any, otherwise print null.
		System.out.println("Saved LilyPond directory: " + prefs.get(PREFS_LILYPOND_LOCATION, null));

		// Check for LilyPond installation - from prefs first
		lilyPondDirectory = new File(prefs.get(PREFS_LILYPOND_LOCATION, getPlatformSpecificDefaultLPDir()));
		if (new File(lilyPondDirectory.getAbsolutePath() + getPlatformSpecificLPExecutable()).exists()) {
			lilyPondAvailable = true;
			System.out.println("LilyPond Found!");
		} else {
			System.out.println("LilyPond Missing!");
		}

		showSplash();
		if (lilyPondAvailable()) {
			try {
				runLilyPond(main_stage);
			} catch (IOException e) {
				Alert alert = new Alert(AlertType.ERROR);
				alert.setTitle("Error");
				alert.setHeaderText("Failed to run LilyPond!");

				alert.show();
			}
		} else {
			splashStage.close();
			loadMainStage(main_stage);
		}
	}

	@Override
	public void stop() {
		mainController.checkSave();
	}

	private void loadMainStage(Stage main_stage) {
		mainStage = main_stage;
		mainStage.setTitle(APP_NAME);
		mainStage.getIcons().add(new Image(getClass().getResourceAsStream("/media/AppIcon.png")));
		loadMainLayout();

		// Ensure that the process exits when the main window is closed
		mainStage.setOnCloseRequest((ev) -> Platform.exit());

		// Start the application maximized.
		// This also mitigates an issue with UI widgets disappearing when hovered (assuming the user keeps it maximized).
		// TODO: That issue needs a more comprehensive fix.
		// https://stackoverflow.com/questions/38308591/javafx-ui-elements-hover-style-not-rendering-correctly-after-resizing-applicatio
		mainStage.setMaximized(true);

		this.mainStage.show();
	}

	private void showSplash() {
		splashPane.setAlignment(Pos.CENTER);
		splashPane.getChildren().setAll(createSplashContent());
		splashPane.setStyle("-fx-background-color: null");

		Scene scene = new Scene(
				splashPane,
				300, 200,
				Color.TRANSPARENT
		);

		splashStage.initStyle(StageStyle.TRANSPARENT);
		splashStage.setScene(scene);
		splashStage.show();

		Rectangle2D primScreenBounds = Screen.getPrimary().getVisualBounds();
		splashStage.setX((primScreenBounds.getWidth() - splashStage.getWidth()) / 2);
		splashStage.setY((primScreenBounds.getHeight() - splashStage.getHeight()) / 2);

		splashBackground.setImage(new Image(getClass().getResourceAsStream("/media/AppIcon.png")));
		splashBackground.setEffect(frostEffect);
	}

	private Node[] createSplashContent() {
		VBox box = new VBox();
		box.setAlignment(Pos.CENTER);

		Text name = new Text(APP_NAME);
		name.setFont(new Font(100));

		Text loading = new Text("Loading...");
		name.setFont(new Font(25));

		box.getChildren().addAll(name, loading);

		return new Node[]{splashBackground, box};
	}

	private void runLilyPond(Stage main_stage) throws IOException {
		// Create the temporary file to hold the lilypond markup
		File lilypondFile = File.createTempFile(MainApp.APP_NAME + "--", "-STARTUP.ly");
		File outputFile = new File(lilypondFile.getAbsolutePath().replace(".ly", ".pdf"));
		lilypondFile.deleteOnExit();
		outputFile.deleteOnExit();

		try {
			LilyPondWriter.ExportResource("renderTemplate.ly", lilypondFile.getAbsolutePath());
		} catch (Exception e) {
			e.printStackTrace();
		}

		LilyPondWriter.executePlatformSpecificLPRender(lilypondFile, false, () -> {
			lilypondFile.delete();
			outputFile.delete();
			Platform.runLater(() -> {
				splashStage.close();
				loadMainStage(main_stage);
			});
		});
	}

	private void loadMainLayout() {
		try {
			// Load layout from fxml file
			FXMLLoader loader = new FXMLLoader();
			loader.setLocation(MainApp.class.getResource("MainScene.fxml"));
			BorderPane rootLayout = loader.load();
			mainController = loader.getController();

			// Apply the layout as the new scene
			Scene scene = new Scene(rootLayout);

			// Set up keyboard shortcuts
			KeyCombination saveCombination = new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN);
			KeyCombination newCombination = new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN);
			KeyCombination openCombination = new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN);

			scene.addEventHandler(KeyEvent.KEY_RELEASED, (event) -> {
				if (saveCombination.match(event)) {
					mainController.handleSave();
				} else if (newCombination.match(event)) {
					mainController.handleNewTone();
				} else if (openCombination.match(event)) {
					mainController.handleOpenTone();
				}
			});

			mainStage.setScene(scene);

			// Attempts to make sure the scene can't be made too small. TODO: It's still possible to make it a bit too small.
			mainStage.setMinWidth(rootLayout.getPrefWidth());
			mainStage.setMinHeight(rootLayout.getPrefHeight());

			// Workaround for Mac bug where resizing is impossible after exiting fullscreen
			// https://stackoverflow.com/questions/47476328/how-to-make-main-javafx-window-still-resizable-coming-back-from-full-screen-mode
			mainStage.fullScreenProperty().addListener((v, o, n) -> {
				if (!mainStage.isFullScreen()) {
					mainStage.setResizable(false);
					mainStage.setResizable(true);
				}
			});

			mainController.setStage(mainStage);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static boolean lilyPondAvailable() {
		return lilyPondAvailable;
	}

	public static String getLilyPondPath() {
		return lilyPondDirectory.getAbsolutePath();
	}

	// Returns the path from the LilyPond directory to the executable itself.
	public static String getPlatformSpecificLPExecutable() {
		if (OS_NAME.startsWith("win")) {
			return "\\lilypond.exe";
		}
		if (OS_NAME.startsWith("mac")) {
			return "/LilyPond.app/Contents/Resources/bin/lilypond";
		}
		if (OS_NAME.startsWith("lin")) {
			return "/lilypond";
		} else return null;
	}

	// Returns the default directory where LilyPond is installed.
	private static String getPlatformSpecificDefaultLPDir() {
		if (OS_NAME.startsWith("win")) {
			return System.getenv("ProgramFiles(X86)") + "\\LilyPond\\usr\\bin";
		}
		if (OS_NAME.startsWith("mac")) {
			return "/Applications";
		}
		if (OS_NAME.startsWith("lin")) {
			return "/usr/bin";
		} else return null;
	}

	// Returns the extension for midi files produced by LilyPond on the current platform.
	static String getPlatformSpecificMidiExtension() {
		if (OS_NAME.startsWith("win")) {
			return ".mid";
		}
		if (OS_NAME.startsWith("mac")) {
			return ".midi";
		}
		if (OS_NAME.startsWith("lin")) {
			return ".midi";
		} else return null;
	}

}
