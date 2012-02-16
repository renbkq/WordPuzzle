package zyf.game.wordpuzzle;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class PuzzleActivity extends Activity implements OnTouchListener, OnGestureListener{

	private PuzzleView puzzle_view;
	private PuzzleData puzzle_data; // puzzle database
	
	public static final int FIRST_PUZZLE_ID = 1;
	public static final String KEY_PUZZLE_ID = "zyf.game.wordpuzzle.puzzle_id";
	
	private static final String TAG = "PuzzleActivity";
	
	private int puzzle_id;
	private char[] puzzle_content;
	private int puzzle_size;
	private int selX = -1;
	private int selY = -1;
	
	private TextView horizontal_text, vertical_text;
	private ViewFlipper word_desc_view;
	private GestureDetector mGestureDetector;
	
	public PuzzleActivity() {
		puzzle_data = new PuzzleData(this);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// receive puzzle id firstly, then get all the detail of puzzle based on this id
		puzzle_id = getIntent().getIntExtra(KEY_PUZZLE_ID, FIRST_PUZZLE_ID);
		Log.d(TAG, "puzzle_id " + puzzle_id);
		initPuzzle();
		mGestureDetector = new GestureDetector((OnGestureListener) this);
		this.requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.puzzle);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.puzzle_title);
		// set square puzzleView below TextViews which show word description
		LinearLayout puzzle_layout = (LinearLayout) findViewById(R.id.puzzle_layout);
		puzzle_view = new PuzzleView(this);
		puzzle_view.setSelectedTile(selX, selY);
		puzzle_view.requestFocus();
		puzzle_layout.addView(puzzle_view);
		
		word_desc_view = (ViewFlipper) findViewById(R.id.word_desc);
		horizontal_text = new TextView(this);
		vertical_text = new TextView(this);

		setWordDesc(selX, selY);
		word_desc_view.setOnTouchListener(this);
		word_desc_view.setClickable(true);
		
		// TBD set button listener for title button
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		SQLiteDatabase db = puzzle_data.getWritableDatabase();
		ContentValues cv = new ContentValues();
		cv.put(PuzzleListDDL.CONTENT, new String(puzzle_content));
		String[] args = {String.valueOf(puzzle_id)};
		db.update(PuzzleListDDL.TABLE_NAME, cv, "_id=?", args);
	}
	
	public void initPuzzle() {
		SQLiteDatabase db = puzzle_data.getReadableDatabase();
		String[] cols = {PuzzleListDDL.CONTENT, PuzzleListDDL.SIZE, PuzzleListDDL.SELECTX, PuzzleListDDL.SELECTY};
		Cursor cursor = db.query(PuzzleListDDL.TABLE_NAME, cols, 
				"_id = " + this.puzzle_id, null, null, null, null);
		if (cursor == null) {
			Toast err_toast = Toast.makeText(this, 
					"Your selected puzzle doesn't exist", Toast.LENGTH_SHORT);
			err_toast.setGravity(Gravity.CENTER, 0, 0);
			err_toast.show();
			this.finish();
		}
		startManagingCursor(cursor);
		if (cursor.moveToNext()) {
			String content = cursor.getString(0);
			puzzle_content = new char[content.length()];
			for (int i = 0; i < content.length(); i++) {
				puzzle_content[i] = content.charAt(i);
			}
			puzzle_size = cursor.getInt(1);
			selX = cursor.getInt(2);
			selY = cursor.getInt(3);
			if (selX == -1 || selY == -1) { // if there is no selected tile, will use the first blank as the selected tile
				for(int i = 0; i < puzzle_size; i++) {
					if (puzzle_content[i] != '*') {
						selX = i % puzzle_size;
						selY = i / puzzle_size;
						break;
					}
				}
			}
		}
	}
	
	public int getPuzzleSize() {
		return puzzle_size;
	}
	
	public char getTile(int x, int y) {
		return puzzle_content[x + y * puzzle_size];
	}
	
	public boolean isValidTile(int x, int y) {
		if (x < 0 || x >= puzzle_size || y < 0 || y >= puzzle_size ||
				puzzle_content[x + y * puzzle_size] == '*') {
			return false;
		}
		return true;
	}

	public boolean getLatitude(int x, int y) {
		if ( (x == 0 && puzzle_content[x + y * puzzle_size + 1] != '*')
			|| (x == puzzle_size-1 && puzzle_content[x + y * puzzle_size -1] != '*')
			|| (x > 0 && x < puzzle_size-1 && puzzle_content[x + y * puzzle_size + 1] != '*')
			|| (x > 0 && x < puzzle_size-1 && puzzle_content[x + y * puzzle_size - 1] != '*') ) {
			return true;
		}
		return false;
	}

	public void setTileContent(String content, int x, int y,
			boolean latitude) {
		for (int i = 0; i < content.length(); ++i) {
			if (puzzle_content[x + y * puzzle_size] == '*')
				break;
			puzzle_content[x + y * puzzle_size] = content.charAt(i);
			if (latitude) {
				++x;
			} else {
				++y;
			}
		}
	}

	public void setWordDesc(int x, int y) {
		this.word_desc_view.removeAllViews();
		String text = "";
		Log.d(TAG, "x=" + String.valueOf(x) + ";y=" + String.valueOf(y));
		SQLiteDatabase db = puzzle_data.getReadableDatabase();
		String[] From = {WordDescDDL.DESCRIPTION,
						 WordDescDDL.LENGTH, 
						 WordDescDDL.STARTX, 
						 WordDescDDL.STARTY};
		String selection = WordDescDDL.PUZZLEID + " = " + String.valueOf(puzzle_id)
				+ " AND " + WordDescDDL.LATITUDE + "= ";
		
		// find horizontal word description
		Cursor cursor = db.query(WordDescDDL.TABLE_NAME, From, 				
				selection + String.valueOf(0), null, null, null, null);
		while (cursor.moveToNext()) {
			if (y == cursor.getInt(3) 
					&& x >= cursor.getInt(2) 
					&& x <= cursor.getInt(2) + cursor.getInt(1)) {
				text = cursor.getString(0);
				break;
			}
		}
		if (text != "") {
			horizontal_text.setText(text);
			word_desc_view.addView(horizontal_text);
		}
		text = "";
		// find vertical word description
		cursor = db.query(WordDescDDL.TABLE_NAME, From, 				
				selection + String.valueOf(1), null, null, null, null);
		
		while (cursor.moveToNext()) {
			if (x == cursor.getInt(2) 
					&& y >= cursor.getInt(3) 
					&& y <= cursor.getInt(3) + cursor.getInt(1)) {
				text = cursor.getString(0);
			}
		}
		if (text != "") {
			vertical_text.setText(text);
			word_desc_view.addView(vertical_text);
		}
		db.close();
	}

	@Override
	public boolean onDown(MotionEvent e) {
		return false;
	}

    private final static int verticalMinDistance = 20;
    private final static int minVelocity         = 0;
	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		if (e1.getX() - e2.getX() > verticalMinDistance 
				&& Math.abs(velocityX) > minVelocity) {
			this.showPrev();
		} else if (e1.getX() - e2.getX() < verticalMinDistance
				&& Math.abs(velocityX) > minVelocity) {
			this.showNext();
		}
		return false;
	}

	private void showPrev() {
		if (word_desc_view.getChildCount() < 2) return;
		word_desc_view.setInAnimation(inFromRightAnimation());
		word_desc_view.setOutAnimation(outToLeftAnimation());
		word_desc_view.showNext();
	}

	private void showNext() {
		if (word_desc_view.getChildCount() < 2) return;
		word_desc_view.setInAnimation(inFromLeftAnimation());
		word_desc_view.setOutAnimation(outToRightAnimation());
		word_desc_view.showPrevious();
	}

	@Override
	public void onLongPress(MotionEvent e) {
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		return false;
	}

	@Override
	public void onShowPress(MotionEvent e) {
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		return mGestureDetector.onTouchEvent(event);
	}
	
	protected Animation inFromRightAnimation() {
		Animation inFromRight = new TranslateAnimation(
				Animation.RELATIVE_TO_PARENT, +1.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f);
		inFromRight.setDuration(500);
		inFromRight.setInterpolator(new AccelerateInterpolator());
		return inFromRight;
	}
	protected Animation outToLeftAnimation() {
        Animation outToLeft = new TranslateAnimation(
                        Animation.RELATIVE_TO_PARENT, 0.0f,
                        Animation.RELATIVE_TO_PARENT, -1.0f,
                        Animation.RELATIVE_TO_PARENT, 0.0f,
                        Animation.RELATIVE_TO_PARENT, 0.0f);
        outToLeft.setDuration(500);
        outToLeft.setInterpolator(new AccelerateInterpolator());
        return outToLeft;
	}
	protected Animation inFromLeftAnimation() {
         Animation inFromLeft = new TranslateAnimation(
                         Animation.RELATIVE_TO_PARENT, -1.0f,
                         Animation.RELATIVE_TO_PARENT, 0.0f,
                         Animation.RELATIVE_TO_PARENT, 0.0f,
                         Animation.RELATIVE_TO_PARENT, 0.0f);
         inFromLeft.setDuration(500);
         inFromLeft.setInterpolator(new AccelerateInterpolator());
         return inFromLeft;
	}
	protected Animation outToRightAnimation() {
        Animation outToRight = new TranslateAnimation(
                        Animation.RELATIVE_TO_PARENT, 0.0f,
                        Animation.RELATIVE_TO_PARENT, +1.0f,
                        Animation.RELATIVE_TO_PARENT, 0.0f,
                        Animation.RELATIVE_TO_PARENT, 0.0f);
        outToRight.setDuration(500);
        outToRight.setInterpolator(new AccelerateInterpolator());
        return outToRight;
	}
}
