package com.example.xyzreader.ui;

// import android.app.Fragment;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
// import android.app.LoaderManager;
import androidx.loader.app.LoaderManager;
import android.content.Intent;
// import android.content.Loader;
import androidx.loader.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import android.os.Bundle;
import androidx.core.app.ShareCompat;
// import android.support.v7.graphics.Palette;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ArticleDetailFragment";

    public static final String ARG_ITEM_ID = "item_id";
    private static final float PARALLAX_FACTOR = 1.25f;

    private Cursor mCursor;
    private long mItemId;
    private View mRootView;
    private int mMutedColor = 0xFF333333;
    private ObservableScrollView mScrollView;
    private DrawInsetsFrameLayout mDrawInsetsFrameLayout;
    private ColorDrawable mStatusBarColorDrawable;

    private int mTopInset;
    private View mPhotoContainerView;
    private ImageView mPhotoView;
    private int mScrollY;
    private boolean mIsCard = false;
    private int mStatusBarFullOpacityBottom;

    private RecyclerView mRecyclerView;
    private ArticleRecyclerViewAdapter mAdapter;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }

        mIsCard = getResources().getBoolean(R.bool.detail_is_card);
        mStatusBarFullOpacityBottom = getResources().getDimensionPixelSize(
                R.dimen.detail_card_top_margin);
        setHasOptionsMenu(true);
    }

    public ArticleDetailActivity getActivityCast() {
        return (ArticleDetailActivity) getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, (androidx.loader.app.LoaderManager.LoaderCallbacks<Cursor>) this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        mDrawInsetsFrameLayout = (DrawInsetsFrameLayout)
                mRootView.findViewById(R.id.draw_insets_frame_layout);
        mDrawInsetsFrameLayout.setOnInsetsCallback(new DrawInsetsFrameLayout.OnInsetsCallback() {
            @Override
            public void onInsetsChanged(Rect insets) {
                mTopInset = insets.top;
            }
        });

        //mScrollView = (ObservableScrollView) mRootView.findViewById(R.id.scrollview);
        /*mScrollView.setCallbacks(new ObservableScrollView.Callbacks() {
            @Override
            public void onScrollChanged() {
                mScrollY = mScrollView.getScrollY();
                getActivityCast().onUpButtonFloorChanged(mItemId, ArticleDetailFragment.this);
                mPhotoContainerView.setTranslationY((int) (mScrollY - mScrollY / PARALLAX_FACTOR));
                updateStatusBar();
            }
        });*/

        /*mPhotoView = (ImageView) mRootView.findViewById(R.id.photo);
        mPhotoContainerView = mRootView.findViewById(R.id.photo_container);*/

        mStatusBarColorDrawable = new ColorDrawable(0);

        mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });

        // setup recycler view
        mRecyclerView = (RecyclerView) mRootView.findViewById(R.id.article_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mRootView.getContext(), LinearLayoutManager.VERTICAL, false));
        mRecyclerView.setHasFixedSize(true);
        mAdapter = new ArticleRecyclerViewAdapter();
        mRecyclerView.setAdapter(mAdapter);

        bindViews();
        updateStatusBar();
        return mRootView;
    }

    private void updateStatusBar() {
        int color = 0;
        if (mPhotoView != null && mTopInset != 0 && mScrollY > 0) {
            float f = progress(mScrollY,
                    mStatusBarFullOpacityBottom - mTopInset * 3,
                    mStatusBarFullOpacityBottom - mTopInset);
            color = Color.argb((int) (255 * f),
                    (int) (Color.red(mMutedColor) * 0.9),
                    (int) (Color.green(mMutedColor) * 0.9),
                    (int) (Color.blue(mMutedColor) * 0.9));
        }
        mStatusBarColorDrawable.setColor(color);
        mDrawInsetsFrameLayout.setInsetBackground(mStatusBarColorDrawable);
    }

    static float progress(float v, float min, float max) {
        return constrain((v - min) / (max - min), 0, 1);
    }

    static float constrain(float val, float min, float max) {
        if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        } else {
            return val;
        }
    }

    private Date parsePublishedDate() {
        try {
            String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
            return dateFormat.parse(date);
        } catch (ParseException ex) {
            Log.e(TAG, ex.getMessage());
            Log.i(TAG, "passing today's date");
            return new Date();
        }
    }

    private void bindViews() {
        if (mRootView == null) {
            return;
        }

        /*TextView titleView = (TextView) mRootView.findViewById(R.id.article_title);
        TextView bylineView = (TextView) mRootView.findViewById(R.id.article_byline);
        bylineView.setMovementMethod(new LinkMovementMethod());
        TextView bodyView = (TextView) mRootView.findViewById(R.id.article_body);*/



        // We will pass a list containing the necessary information to populate the recycler view
        // Some things may be best to do when creating and inflating the views in the viewholder constructors

        // Primarily, the Adapter will need to know how many items to handle
        // Since it is a nested class in this scenario, it has access to all the data

        // mCursor.getString(ArticleLoader.Query.TITLE) will get the title
        // Date publishedDate = parsePublishedDate() will get the published date plus logic structure after it

        // bodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Rosario-Regular.ttf"));

        if (mCursor != null) {
            mAdapter.setData();

            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1);
            /*
            titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                bylineView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + " by <font color='#ffffff'>"
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));

            } else {
                // If date is before 1902, just show the string
                bylineView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate) + " by <font color='#ffffff'>"
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));

            }
            // this is where the text body is received and passed to the text view.
            // How do we separate this into a list of separate strings representing paragraphs?
            // The starter code seems to separate things based on \r\n|\n
            bodyView.setText(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY).replaceAll("(\r\n|\n)", "<br />")));
*/
//            // this is where the article image is received and passed to the image view
//            ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
//                    .get(mCursor.getString(ArticleLoader.Query.PHOTO_URL), new ImageLoader.ImageListener() {
//                        @Override
//                        public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
//                            Bitmap bitmap = imageContainer.getBitmap();
//                            if (bitmap != null) {
//                                Palette p = Palette.generate(bitmap, 12);
//                                mMutedColor = p.getDarkMutedColor(0xFF333333);
//                                mPhotoView.setImageBitmap(imageContainer.getBitmap());
//                                mRootView.findViewById(R.id.meta_bar)
//                                        .setBackgroundColor(mMutedColor);
//                                updateStatusBar();
//                            }
//                        }
//
//                        @Override
//                        public void onErrorResponse(VolleyError volleyError) {
//
//                        }
//                    });
        } else {
            mRootView.setVisibility(View.GONE);
            //titleView.setText("N/A");
            //bylineView.setText("N/A" );
            //bodyView.setText("N/A");
        }
    }

    @Override
    public androidx.loader.content.Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }

        bindViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }

    public int getUpButtonFloor() {
        if (mPhotoContainerView == null || mPhotoView.getHeight() == 0) {
            return Integer.MAX_VALUE;
        }

        // account for parallax
        return mIsCard
                ? (int) mPhotoContainerView.getTranslationY() + mPhotoView.getHeight() - mScrollY
                : mPhotoView.getHeight() - mScrollY;
    }

    public class ArticleRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private String[] mBodyText;
        private final int IMAGE_TYPE = 0;
        private final int TITLE_TYPE = 1;
        private final int TEXT_TYPE = 2;

        public void setData() {
            mBodyText = mCursor.getString(ArticleLoader.Query.BODY).split("(\r\n\r\n)");
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            if (position > 1) return TEXT_TYPE;
            if (position > 0) return TITLE_TYPE;
            return IMAGE_TYPE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
            if (viewType == IMAGE_TYPE) {
                View view = layoutInflater.inflate(R.layout.article_image_item, parent, false);
                return new ArticleImageViewHolder(view);
            }
            if (viewType == TITLE_TYPE) {
                View view = layoutInflater.inflate(R.layout.article_title_item, parent, false);
                return new ArticleTitleViewHolder(view);
            }
            View view = layoutInflater.inflate(R.layout.article_text_item, parent, false);
            return new ArticleTextViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                // bind the image viewholder with data
                final ArticleImageViewHolder viewHolder = (ArticleImageViewHolder) holder;
                ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                        .get(mCursor.getString(ArticleLoader.Query.PHOTO_URL), new ImageLoader.ImageListener() {
                            @Override
                            public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                                Bitmap bitmap = imageContainer.getBitmap();
                                if (bitmap != null) {
                                    viewHolder.mImageView.setImageBitmap(imageContainer.getBitmap());
                                }
                            }

                            @Override
                            public void onErrorResponse(VolleyError volleyError) {

                            }
                        });
            }
            else if (position == 1) {
                ArticleTitleViewHolder viewHolder = (ArticleTitleViewHolder) holder;
                viewHolder.mArticleTitle.setText(mCursor.getString(ArticleLoader.Query.TITLE));
                Date publishedDate = parsePublishedDate();
                if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                    viewHolder.mArticleByline.setText(Html.fromHtml(
                            DateUtils.getRelativeTimeSpanString(
                                    publishedDate.getTime(),
                                    System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                    DateUtils.FORMAT_ABBREV_ALL).toString()
                                    + " by <font color='#ffffff'>"
                                    + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                    + "</font>"));

                } else {
                    // If date is before 1902, just show the string
                    viewHolder.mArticleByline.setText(Html.fromHtml(
                            outputFormat.format(publishedDate) + " by <font color='#ffffff'>"
                                    + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                    + "</font>"));

                }
            }
            else {
                // bind the text viewholder with data
                ArticleTextViewHolder viewHolder = (ArticleTextViewHolder) holder;
                String text = mBodyText[position - 2];
                text = text.replaceAll("\r\n|\n", " ");
                text += "\n";
                viewHolder.mArticleBody.setText(text);
            }
        }

        @Override
        public int getItemCount() {
            if (mBodyText.length > 0) {
                return mBodyText.length + 2;
            }
            return 0;
        }

        public class ArticleImageViewHolder extends RecyclerView.ViewHolder {
            public ImageView mImageView;
            public View mImageContainer;

            public ArticleImageViewHolder(@NonNull View itemView) {
                super(itemView);
                mImageView = (ImageView) itemView.findViewById(R.id.photo);
                mImageContainer = itemView.findViewById(R.id.photo_container);
            }
        }

        public class ArticleTitleViewHolder extends RecyclerView.ViewHolder {
            public View mMetaBar;
            public TextView mArticleTitle;
            public TextView mArticleByline;

            public ArticleTitleViewHolder(@NonNull View itemView) {
                super(itemView);
                mMetaBar = itemView.findViewById(R.id.meta_bar);
                mArticleTitle = itemView.findViewById(R.id.article_title);
                mArticleByline = itemView.findViewById(R.id.article_byline);
            }
        }

        public class ArticleTextViewHolder extends RecyclerView.ViewHolder {
            public TextView mArticleBody;

            public ArticleTextViewHolder(@NonNull View itemView) {
                super(itemView);
                mArticleBody = itemView.findViewById(R.id.article_body);
            }
        }
    }
}
