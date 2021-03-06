package org.duckdns.toserba23.toserba23.fragment;

import android.app.Dialog;
import android.app.LoaderManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Loader;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.duckdns.toserba23.toserba23.R;
import org.duckdns.toserba23.toserba23.adapter.SaleOrderAdapter;
import org.duckdns.toserba23.toserba23.loader.GenericModelLoader;
import org.duckdns.toserba23.toserba23.loader.ProductTemplateDetailLoader;
import org.duckdns.toserba23.toserba23.loader.SaleOrderLoader;
import org.duckdns.toserba23.toserba23.loader.SaleOrderSaveLoader;
import org.duckdns.toserba23.toserba23.model.AccessRight;
import org.duckdns.toserba23.toserba23.model.GenericModel;
import org.duckdns.toserba23.toserba23.model.ProductPricelistItem;
import org.duckdns.toserba23.toserba23.model.ProductTemplate;
import org.duckdns.toserba23.toserba23.model.SaleOrder;
import org.duckdns.toserba23.toserba23.model.SaleOrderLine;
import org.duckdns.toserba23.toserba23.utils.DisplayFormatter;
import org.duckdns.toserba23.toserba23.utils.QueryUtils;
import org.duckdns.toserba23.toserba23.utils.QueryUtilsAccessRight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by ryanto on 24/02/18.
 */

public class ProductDetail extends AppCompatActivity {

    private static final int FETCH_PRODUCT_TEMPLATE_DETAIL_LOADER_ID = 1;
    private static final int FETCH_SALES_ORDER_LOADER_ID = 2;
    private static final int FETCH_STOCK_WAREHOUSE_LOADER_ID = 3;
    private static final int SAVE_SALES_ORDER_LOADER_ID = 4;
    private static final int SAVE_SALES_ORDER_LINE_LOADER_ID = 5;

    private SaleOrderAdapter mAdapter;
    private ProductTemplate mProductTemplate;

    Toolbar mToolbar;
    LinearLayout mPricelistViewContainer;

    private SharedPreferences mPref;
    private int PRIVATE_MODE = 0;

    // Account information for xmlrpc
    private String mUrl;
    private String mDatabaseName;
    private int mUserId;
    private String mPassword;
    private int mProductTmplId;
    private int mDefPartnerId;
    private String mDefPartnerName;
    private AccessRight mAccess;

    private ArrayList<Object[]> mSaleOrderFilterElements = new ArrayList<Object[]>(){};

    private SaleOrder mOrderToSave;
    private SaleOrderLine mOrderLineToSave;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize account information with data from Preferences and bundle
        mPref = this.getSharedPreferences(getString(R.string.settings_shared_preferences_label), PRIVATE_MODE);
        mUrl = mPref.getString(getString(R.string.settings_url_key), null);
        mDatabaseName = mPref.getString(getString(R.string.settings_database_name__key), null);
        mUserId = mPref.getInt(getString(R.string.settings_user_id_key), 0);
        mPassword = mPref.getString(getString(R.string.settings_password_key), null);
        mProductTmplId = getIntent().getIntExtra("product_tmpl_id", 0);
        mDefPartnerId = mPref.getInt(getString(R.string.settings_def_partner_id_key), 0);
        mDefPartnerName = mPref.getString(getString(R.string.settings_def_partner_name_key), null);
        mAccess = getIntent().getParcelableExtra(QueryUtilsAccessRight.ACCESS_RIGHT);
        setTitle(getString(R.string.detail_product_activity_label));

        setContentView(R.layout.product_template_detail_app_bar);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAccess != null & mAccess.has_access_to_product) {
                    if (mDefPartnerName != null) {
                        load_related_so();
                    } else {
                        Toast.makeText(ProductDetail.this, R.string.error_no_default_partner, Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(ProductDetail.this, R.string.no_access_right_error, Toast.LENGTH_LONG).show();
                }
            }
        });

        // Set pricelist container view
        mPricelistViewContainer = (LinearLayout) findViewById(R.id.container_view);

        mAdapter = new SaleOrderAdapter(ProductDetail.this, new ArrayList<SaleOrder>());

        readData();
    }

    public void readData() {
        // Get a reference to the ConnectivityManager to check state of network connectivity
        ConnectivityManager connMgr = (ConnectivityManager)
                this.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Get details on the currently active default data network
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        // If there is a network connection, fetch data
        if (networkInfo != null && networkInfo.isConnected()) {
            // Get a reference to the LoaderManager, in order to interact with loaders.
            LoaderManager loaderManager = getLoaderManager();

            // number the loaderManager with mPage as may be requesting up to three lots of JSON for each tab
            loaderManager.restartLoader(FETCH_PRODUCT_TEMPLATE_DETAIL_LOADER_ID, null, loadProductTemplateFromServerListener);
        } else {
            // Otherwise, display error
            // First, hide loading indicator so error message will be visible
            View loadingIndicator = findViewById(R.id.loading_spinner);
            loadingIndicator.setVisibility(View.GONE);

            // Update empty state with no connection error message
            TextView noConnectionView = (TextView) findViewById(R.id.empty_view);
            noConnectionView.setText(getString(R.string.error_no_internet_connection));
            noConnectionView.setVisibility(View.VISIBLE);
        }
    }

    /**
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.product_template_detail_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to a click on the "Insert dummy data" menu option
            case R.id.add_to_sale_order:
                if (mAccess != null & mAccess.has_access_to_product) {
                    if (mDefPartnerName != null) {
                        load_related_so();
                    } else {
                        Toast.makeText(ProductDetail.this, R.string.error_no_default_partner, Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(ProductDetail.this, R.string.no_access_right_error, Toast.LENGTH_LONG).show();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    */

    public void load_related_so() {
        // Get a reference to the ConnectivityManager to check state of network connectivity
        ConnectivityManager connMgr = (ConnectivityManager)
                this.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Get details on the currently active default data network
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        // If there is a network connection, fetch data
        if (networkInfo != null && networkInfo.isConnected()) {
            // Get a reference to the LoaderManager, in order to interact with loaders.
            LoaderManager loaderManager = getLoaderManager();

            // number the loaderManager with mPage as may be requesting up to three lots of JSON for each tab
            loaderManager.restartLoader(FETCH_SALES_ORDER_LOADER_ID, null, loadSaleOrderFromServerListener);
        } else {
            // Otherwise, display error
            Toast.makeText(this, R.string.error_no_internet_connection, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Setup Loader behavior here
     * @param i id of the called loader
     * @param bundle
     * @return
     */
    private LoaderManager.LoaderCallbacks<ProductTemplate> loadProductTemplateFromServerListener = new LoaderManager.LoaderCallbacks<ProductTemplate>() {
        @Override
        public Loader<ProductTemplate> onCreateLoader(int i, Bundle bundle) {
            // Show loading indicator
            View loadingIndicator = findViewById(R.id.loading_spinner);
            loadingIndicator.setVisibility(View.VISIBLE);

            // Start appropriate loader to "read" or "save" data to/from server.
            // Default to "read" data from server
            return new ProductTemplateDetailLoader(ProductDetail.this, mUrl, mDatabaseName, mUserId, mPassword, mProductTmplId);
        }
        @Override
        public void onLoadFinished(Loader<ProductTemplate> loader, ProductTemplate productTemplate) {
            // Hide loading indicator because the data has been loaded
            View loadingIndicator = findViewById(R.id.loading_spinner);
            loadingIndicator.setVisibility(View.GONE);

            // Keep data as this class attributes and update view
            mProductTemplate = productTemplate;
            displayUpdate(productTemplate);
            getLoaderManager().destroyLoader(FETCH_PRODUCT_TEMPLATE_DETAIL_LOADER_ID);
        }
        @Override
        public void onLoaderReset(Loader<ProductTemplate> loader) {
        }
    };

    private void displayUpdate(ProductTemplate productTemplate) {
        // If there is a valid list of {@link stock picking}s, then add them to the adapter's
        // data set. This will trigger the ListView to update.
        if (productTemplate != null) {
            // Display detailed view header document
            ((TextView) findViewById(R.id.detail_code)).setText(productTemplate.getRef());
            ((TextView) findViewById(R.id.detail_name)).setText(productTemplate.getName());
            ((TextView) findViewById(R.id.detail_category)).setText(productTemplate.getProductCategory().getName());
            ((TextView) findViewById(R.id.detail_uom)).setText(productTemplate.getProductUom().getName());
            ((TextView) findViewById(R.id.detail_qty_ckl)).setText(DisplayFormatter.formatQuantity(productTemplate.getProductProduct().getQtyCKL()));
            ((TextView) findViewById(R.id.detail_qty2_ckl)).setText(DisplayFormatter.formatQuantity(productTemplate.getProductProduct().getQtyForecastCKL()));
            ((TextView) findViewById(R.id.detail_qty_prl)).setText(DisplayFormatter.formatQuantity(productTemplate.getProductProduct().getQtyPRL()));
            ((TextView) findViewById(R.id.detail_qty2_prl)).setText(DisplayFormatter.formatQuantity(productTemplate.getProductProduct().getQtyForecastPRL()));

            // Prepare linear layout view which will contain inflated product row view
            LayoutInflater internalInflater = LayoutInflater.from(getApplicationContext());
            ArrayList<ProductPricelistItem> productPricelists = mProductTemplate.getProductPricelistItem();

            // Display pricelist data onto product row which will be inflated based on number of products to be displayed
            if (productPricelists !=null && !productPricelists.isEmpty()) {
                for (int i = 0; i < productPricelists.size(); i++) {
                    ProductPricelistItem productPricelist = productPricelists.get(i);
                    View rowView = internalInflater.inflate(R.layout.product_template_detail_pricelist_adapter, mPricelistViewContainer, false);
                    ((TextView) rowView.findViewById(R.id.date_text_view)).setText(DisplayFormatter.formatDate(productPricelist.getDateStart()));
                    ((TextView) rowView.findViewById(R.id.pricelist_name)).setText(DisplayFormatter.formatString(productPricelist.getPricelistName()));
                    ((TextView) rowView.findViewById(R.id.fixed_price)).setText(DisplayFormatter.formatCurrency(productPricelist.getFixedPrice()));
                    ((TextView) rowView.findViewById(R.id.min_qty)).setText(DisplayFormatter.formatQuantity(productPricelist.getMinQuantity()));
                    ((TextView) rowView.findViewById(R.id.notes)).setText(DisplayFormatter.formatString(productPricelist.getXNotes()));
                    mPricelistViewContainer.addView(rowView);
                }
            }
        } else {
            Toast.makeText(this, R.string.error_cannot_connect_to_server, Toast.LENGTH_LONG).show();
        }
    }

    private LoaderManager.LoaderCallbacks<List<SaleOrder>> loadSaleOrderFromServerListener = new LoaderManager.LoaderCallbacks<List<SaleOrder>>() {
        @Override
        public Loader<List<SaleOrder>> onCreateLoader(int i, Bundle bundle) {
            mSaleOrderFilterElements.clear();
            mSaleOrderFilterElements.add(new Object[] {"state", "=", "draft"});
            mSaleOrderFilterElements.add(new Object[] {"partner_id", "ilike", mDefPartnerName});
            Object[] filterArray = new Object[] {
                    mSaleOrderFilterElements
            };
            return new SaleOrderLoader(ProductDetail.this, mUrl, mDatabaseName, mUserId, mPassword, filterArray);
        }

        @Override
        public void onLoadFinished(Loader<List<SaleOrder>> loader, List<SaleOrder> saleOrders) {
            // Clear the adapter of previous sale order
            mAdapter.clear();

            if (saleOrders != null && !saleOrders.isEmpty()) {
                Collections.sort(saleOrders, new Comparator<SaleOrder>() {
                    @Override
                    public int compare(SaleOrder item1, SaleOrder item2) {
                        return item2.getName().compareToIgnoreCase(item1.getName());
                    }
                });
                mAdapter.addAll(saleOrders);
                add_product_to_so_dialog(true);
            } else {
                Toast.makeText(ProductDetail.this, R.string.error_order_not_found, Toast.LENGTH_LONG).show();
                add_product_to_so_dialog(false);
            }
            getLoaderManager().destroyLoader(FETCH_SALES_ORDER_LOADER_ID);
        }

        @Override
        public void onLoaderReset(Loader<List<SaleOrder>> loader) {
            // Loader reset, so we can clear out our existing data.
            mAdapter.clear();
        }
    };

    private void add_product_to_so_dialog(Boolean soExist) {
        // show dialog to add to sale order
        final Dialog dialog = new Dialog(ProductDetail.this);
        dialog.setContentView(R.layout.product_template_detail_add_so_dialog);
        ListView listView = (ListView) dialog.findViewById(R.id.list);
        TextView emptyView = (TextView) dialog.findViewById(R.id.empty_view);
        final EditText qtyView = (EditText) dialog.findViewById(R.id.add_to_sale_order_qty);
        final EditText notesView = (EditText) dialog.findViewById(R.id.add_to_sale_order_notes);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                int soId = mAdapter.getItem(i).getId();
                mOrderLineToSave = new SaleOrderLine(
                        0,
                        new GenericModel(mProductTemplate.getId(), mProductTemplate.getName()),
                        notesView.getText().toString(),
                        Integer.parseInt(qtyView.getText().toString()),
                        soId
                );
                save_so_line_to_server(false);
                dialog.dismiss();
            }
        });
        if (!soExist) {
            listView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        }
        dialog.findViewById(R.id.add_to_sale_order_minus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int i = Integer.parseInt(qtyView.getText().toString());
                if (i > 1) { qtyView.setText(String.valueOf(i-1)); }
            }
        });
        dialog.findViewById(R.id.add_to_sale_order_plus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int i = Integer.parseInt(qtyView.getText().toString());
                qtyView.setText(String.valueOf(i+1));
            }
        });

        // create new quotation if create button pressed
        dialog.findViewById(R.id.add_to_sale_order_create).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mOrderLineToSave = new SaleOrderLine(
                        0,
                        new GenericModel(mProductTemplate.getId(), mProductTemplate.getName()),
                        notesView.getText().toString(),
                        Integer.parseInt(qtyView.getText().toString()),
                        0
                );
                save_so_line_to_server(true);
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void save_so_line_to_server(Boolean createNewSo) {
        // Get a reference to the ConnectivityManager to check state of network connectivity
        ConnectivityManager connMgr = (ConnectivityManager)
                this.getSystemService(Context.CONNECTIVITY_SERVICE);
        // Get details on the currently active default data network
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        // If there is a network connection, fetch data
        if (networkInfo != null && networkInfo.isConnected()) {
            // Get a reference to the LoaderManager, in order to interact with loaders.
            LoaderManager loaderManager = getLoaderManager();
            // number the loaderManager with mPage as may be requesting up to three lots of JSON for each tab
            if (createNewSo) {
                loaderManager.restartLoader(FETCH_STOCK_WAREHOUSE_LOADER_ID, null, fetchWarehouseListener);
            } else {
                loaderManager.restartLoader(SAVE_SALES_ORDER_LINE_LOADER_ID, null, saveSaleOrderToServerListener);
            }
        } else {
            Toast.makeText(ProductDetail.this, R.string.error_no_internet_connection, Toast.LENGTH_LONG).show();
        }
    }

    private LoaderManager.LoaderCallbacks<List<GenericModel>> fetchWarehouseListener = new LoaderManager.LoaderCallbacks<List<GenericModel>>() {
        @Override
        public Loader<List<GenericModel>> onCreateLoader(int i, Bundle bundle) {
            Object[] filterArray = new Object[] {
                    new ArrayList<Object[]>(){}
            };
            // Get fields
            HashMap stockWarehouseMap = new HashMap();
            stockWarehouseMap.put("fields", Arrays.asList(
                    "id",
                    "name"
            ));
            return new GenericModelLoader(ProductDetail.this, mUrl, mDatabaseName, mUserId, mPassword, QueryUtils.STOCK_WAREHOUSE, filterArray, stockWarehouseMap);
        }

        @Override
        public void onLoadFinished(Loader<List<GenericModel>> loader, final List<GenericModel> stockWarehouses) {
            if (stockWarehouses != null && !stockWarehouses.isEmpty()) {
                // show warehouse list to choose from
                final Dialog dialog = new Dialog(ProductDetail.this);
                dialog.setContentView(R.layout.simple_list_view);
                ListView listView = (ListView) dialog.findViewById(R.id.list);
                final ArrayList<String> list = new ArrayList<String>();
                for (int i = 0; i < stockWarehouses.size(); ++i) {
                    list.add(stockWarehouses.get(i).getName());
                }
                ArrayAdapter adapter = new ArrayAdapter(ProductDetail.this,android.R.layout.simple_list_item_1, list);
                listView.setAdapter(adapter);
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        mOrderToSave = new SaleOrder(0, new GenericModel(mDefPartnerId, mDefPartnerName), stockWarehouses.get(i));
                        save_new_so_to_server();
                        dialog.dismiss();
                    }
                });
                dialog.show();
            } else {
                Toast.makeText(ProductDetail.this, R.string.detail_product_options_add_so_failed, Toast.LENGTH_LONG).show();
            }
            getLoaderManager().destroyLoader(FETCH_STOCK_WAREHOUSE_LOADER_ID);
        }

        @Override
        public void onLoaderReset(Loader<List<GenericModel>> loader) {
        }
    };

    private void save_new_so_to_server() {
        // Get a reference to the ConnectivityManager to check state of network connectivity
        ConnectivityManager connMgr = (ConnectivityManager)
                this.getSystemService(Context.CONNECTIVITY_SERVICE);
        // Get details on the currently active default data network
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        // If there is a network connection, fetch data
        if (networkInfo != null && networkInfo.isConnected()) {
            // Get a reference to the LoaderManager, in order to interact with loaders.
            LoaderManager loaderManager = getLoaderManager();
            // number the loaderManager with mPage as may be requesting up to three lots of JSON for each tab
            loaderManager.restartLoader(SAVE_SALES_ORDER_LOADER_ID, null, saveSaleOrderToServerListener);
        } else {
            Toast.makeText(ProductDetail.this, R.string.error_no_internet_connection, Toast.LENGTH_LONG).show();
        }
    }

    private LoaderManager.LoaderCallbacks<List<Integer>> saveSaleOrderToServerListener = new LoaderManager.LoaderCallbacks<List<Integer>>() {
        @Override
        public Loader<List<Integer>> onCreateLoader(int i, Bundle bundle) {
            if ( i == SAVE_SALES_ORDER_LOADER_ID) {
                return new SaleOrderSaveLoader(ProductDetail.this, mUrl, mDatabaseName, mUserId, mPassword, 0, mOrderToSave.getHashmap(), true);
            } else {
                return new SaleOrderSaveLoader(ProductDetail.this, mUrl, mDatabaseName, mUserId, mPassword, 0, mOrderLineToSave.getHashmap());
            }
        }

        @Override
        public void onLoadFinished(Loader<List<Integer>> loader, List<Integer> createdIds) {
            if (createdIds != null && !createdIds.isEmpty()) {
                Toast.makeText(ProductDetail.this, R.string.detail_product_options_add_so_success, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(ProductDetail.this, R.string.detail_product_options_add_so_failed, Toast.LENGTH_LONG).show();
            }
            if (loader.getId() == SAVE_SALES_ORDER_LOADER_ID) {
                mOrderLineToSave.setOrderId(createdIds.get(0));
                getLoaderManager().destroyLoader(SAVE_SALES_ORDER_LOADER_ID);
                save_so_line_to_server(false);
            } else if (loader.getId() == SAVE_SALES_ORDER_LINE_LOADER_ID) {
                getLoaderManager().destroyLoader(SAVE_SALES_ORDER_LINE_LOADER_ID);
            }
        }

        @Override
        public void onLoaderReset(Loader<List<Integer>> loader) {
        }
    };
}
