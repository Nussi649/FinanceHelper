package com.privat.pitz.financehelper;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.privat.pitz.financehelper.ui.BudgetAccountListHandler;
import com.privat.pitz.financehelper.core.Util;
import com.privat.pitz.financehelper.data.AccountBE;
import com.privat.pitz.financehelper.data.BudgetAccountBE;
import com.privat.pitz.financehelper.data.ProjectBudgetBE;
import com.privat.pitz.financehelper.ui.BudgetAccountTableRow;
import com.privat.pitz.financehelper.ui.BudgetFigures;
import com.privat.pitz.financehelper.ui.PercentageBackground;
import com.privat.pitz.financehelper.ui.TxListSection;
import com.privat.pitz.financehelper.ui.TxSwipeActions;
import com.privat.pitz.financehelper.ui.adapter.TxListAdapter;
import com.privat.pitz.financehelper.ui.dialog.CreateBudgetAccountDialog;
import com.privat.pitz.financehelper.ui.dialog.EditRenewalDialog;
import com.privat.pitz.financehelper.ui.dialog.SetYearlyBudgetDialog;
import com.privat.pitz.financehelper.ui.dialog.TransferAvailableBudgetDialog;
import com.privat.pitz.financehelper.ui.dialog.TransferSubBudgetDialog;

public class BudgetAccountDetailsActivity extends AbstractActivity implements BudgetAccountListHandler {
    BudgetAccountBE mAccount;
    List<BudgetAccountTableRow> budgetViews = new ArrayList<>();
    LinearLayout rootLayout;
    TextView totalValue;
    TextView totalPercentage;
    TextView totalYearly;
    TextView tvRenewal;

    TxListSection section;
    TxListAdapter listAdapter;
    TextView indivValue;
    TextView indivPercentage;
    TextView indivYearly;

    // region AbstractActivity & Activity Overrides
    @Override
    public void onStart() {
        super.onStart();
        if (!passedOnCreate) {
            reloadUI();
        }
    }

    @Override
    protected void workingThread() {
        AccountBE acc = getModel().currentInspectedAccount;
        if (acc instanceof BudgetAccountBE) {
            mAccount = (BudgetAccountBE) acc;
            loadSubBudgets();
        } else {
            // handle unexpected behaviour
            finish();
        }
    }

    @Override
    protected void endWorkingThread() {
        setContentView(R.layout.activity_budget_account_details);

        rootLayout = findViewById(R.id.root_layout);
        tvRenewal = findViewById(R.id.tv_renewal);

        View containerTx = findViewById(R.id.container_tx_sum);
        indivValue = containerTx.findViewById(R.id.total_current_value);
        indivPercentage = containerTx.findViewById(R.id.total_current_percentage);
        indivYearly = containerTx.findViewById(R.id.total_yearly_budget);

        View containerTotal = findViewById(R.id.container_total_sum);
        totalValue = containerTotal.findViewById(R.id.total_current_value);
        totalPercentage = containerTotal.findViewById(R.id.total_current_percentage);
        totalYearly = containerTotal.findViewById(R.id.total_yearly_budget);

        listAdapter = new TxListAdapter();
        View recyclerView = rootLayout.findViewById(R.id.recyclerView);
        TxSwipeActions swipeActions = new TxSwipeActions(this, controller, mAccount, listAdapter, recyclerView, this);
        section = new TxListSection(
                rootLayout,
                listAdapter,
                () -> mAccount.getTxList(),
                TxListSection.MATCH_DESCRIPTION,
                this::renderTxSum,
                swipeActions);

        // TxListSection hides these two by default (plain asset-account screens don't use them),
        // but the budget screen needs them visible to show percentage/yearly budget alongside
        // the sum - restore what BudgetAccountDetailsActivity.initViews() used to do by
        // re-enabling them after super.initViews() had hidden them.
        indivPercentage.setVisibility(View.VISIBLE);
        indivYearly.setVisibility(View.VISIBLE);

        // The inherited populateUI() used to force this label to the short "Σ" form (rather than
        // the layout's default "Total Σ") for the per-entry sum card. Preserve that.
        TextView labelSigma = containerTx.findViewById(R.id.label_sigma);
        labelSigma.setText(R.string.label_sum_tx);

        populateUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_budgetaccount_details, menu);
        // AssetAccountDetailsActivity used to contribute this second menu via its own
        // onCreateOptionsMenu() through the super call. Now that the inheritance link is gone,
        // this activity inflates it directly so "delete budget account" keeps showing up.
        inflater.inflate(R.menu.menu_account_details, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.item_set_yearly_budget) {
            showSetYearlyBudgetDialog();
        } else if (itemId == R.id.item_transfer_budget) {
            showTransferAvailableBudgetDialog();
        } else if (itemId == R.id.item_new_sub_budget) {
            showCreateSubBudgetDialog();
        } else if (itemId == R.id.item_new_project_budget) {
            showCreateProjectBudgetDialog();
        } else if (itemId == R.id.item_transfer_sub_budget) {
            showTransferSubBudgetDialog();
        } else if (itemId == R.id.item_delete_account) {
            // AssetAccountDetailsActivity used to handle this item through the super call.
            // Handle it directly now that this activity no longer extends it.
            deleteAccount();
        }
        return super.onOptionsItemSelected(item);
    }
    // endregion

    @SuppressLint("DefaultLocale")
    private void renderTxSum(float newValue) {
        float allotted_budget = mAccount.getMeanAllottedIndivBudget();

        float current_percentage = Util.calculateAdvancedPercentage(mAccount.indivAvailableBudget, newValue, allotted_budget);
        String currentBudgetString = Util.formatToFixedLength(Util.formatLargeFloatDisplay(mAccount.indivAvailableBudget), 5);
        String currentSumString = String.format("%sx / %sx",
                Util.formatLargeFloatDisplay(newValue),
                currentBudgetString);
        String currentPercentageString = Util.formatPercentage(current_percentage);
        String yearly_budget_string = Util.formatLargeFloatShort(mAccount.indivYearlyBudget) + "x";
        // get currency character
        String currency = getString(R.string.label_currency);
        // set values of total sum text views
        indivValue.setText(currentSumString.replace("x", currency));
        indivPercentage.setText(currentPercentageString);
        indivYearly.setText(yearly_budget_string.replace("x", currency));
    }

    private void setTitle() {
        setCustomTitle();
        setCustomTitleDetails(mAccount.toString());
    }

    @SuppressLint("DefaultLocale")
    private void populateUI() {
        // Reset the search state and repopulate entries against the (possibly freshly reloaded)
        // mAccount data, mirroring the inherited AssetAccountDetailsActivity.populateUI() ->
        // filterEntries(null) call this used to rely on via super.populateUI().
        section.applyFilter(null);
        if (section.isEmpty()) {
            showToastLong(R.string.toast_error_no_entries);
        }

        // if budget is a project budget, color background
        if (mAccount instanceof ProjectBudgetBE) {
            rootLayout.setBackgroundColor(getColor(R.color.colorSecondaryLight));
            findViewById(R.id.renewal_container).setVisibility(View.GONE);
        } else {
            String renewalString = String.format(Locale.US, "%01dM (%s)", mAccount.getRenewalPeriod(), mAccount.getNextRenewal());
            tvRenewal.setText(renewalString);
            tvRenewal.setOnClickListener(v -> showEditRenewalDialog());
        }
        // populate sub budget entries
        // remove all table rows (there is no header)
        TableLayout container = findViewById(R.id.sub_budget_overview_table_layout);
        container.removeAllViews();
        // add the up-to-date BudgetAccountTableRow objects and increase total sum
        for (BudgetAccountTableRow view : budgetViews) {
            view.populateUI();
            container.addView(view);
        }
        updateUITotalSums();
        setTitle();
    }

    @Override
    public void onRefresh() {
        for (BudgetAccountTableRow row : budgetViews)
            row.updateUI();
        renderTxSum(mAccount.getSum());
        updateUITotalSums();
    }

    private void redirectAfterAccountDelete() {
        startActivity(BudgetsActivity.class);
    }

    private void deleteAccount() {
        Dialog.OnClickListener listener = (dialogInterface, i) -> {
            try {
                boolean result = controller.deleteAccount(mAccount);
                if (result) {
                    redirectAfterAccountDelete();
                    showToastLong(R.string.toast_success_delete_account);
                } else {
                    showToastLong(R.string.toast_error_account_not_found);
                }
            } catch (JSONException e) {
                showToastLong(R.string.toast_error_JSONError);
            } catch (IOException e) {
                showToastLong(R.string.toast_error_IOError);
            }
        };
        showConfirmDialog(R.string.question_delete_account, listener);
    }

    // region BudgetAccountListHandler Overrides
    @Override
    public void showAccountDetails(BudgetAccountBE referenceAccount) {
        model.currentInspectedAccount = referenceAccount;
        startActivity(BudgetAccountDetailsActivity.class);
    }

    @Override
    public void addBudgetViewToBackend(BudgetAccountTableRow newItem) {
        budgetViews.add(newItem);
    }

    @Override
    public boolean removeBudgetViewFromBackend(BudgetAccountTableRow removeItem) {
        return budgetViews.remove(removeItem);
    }
    // endregion

    // region Dialogs
    public void showSetYearlyBudgetDialog() {
        SetYearlyBudgetDialog dialog = new SetYearlyBudgetDialog(this, mAccount.indivYearlyBudget) {
            @Override
            public void onConfirm(float newBudget, boolean adjustAvailable) {
                try {
                    controller.updateYearlyBudget(newBudget, mAccount, adjustAvailable);
                    onRefresh();
                    showToastLong(R.string.toast_success_yearly_budget_adjusted);
                } catch (JSONException | IOException e) {
                    showErrorToast(e);
                }
            }
        };
        dialog.show();
    }

    public void showTransferAvailableBudgetDialog() {
        TransferAvailableBudgetDialog dialog = new TransferAvailableBudgetDialog(this, model.getAllBudgetAccounts(), mAccount) {
            @Override
            public void onConfirm(float amount, BudgetAccountBE selectedAccount) {
                try {
                    controller.transferAvailableBudget(amount, mAccount, selectedAccount);
                    onRefresh();
                    showToastLong(R.string.toast_success_available_budget_transferred);
                } catch (JSONException | IOException e) {
                    showErrorToast(e);
                }
            }
        };
        dialog.show();
    }

    public void showCreateSubBudgetDialog() {
        BudgetAccountDetailsActivity self = this;
        CreateBudgetAccountDialog dialog = new CreateBudgetAccountDialog(this) {
            @Override
            public void onConfirm(String subBudgetName, float currentMonthBudget, float yearlyBudget) {
                try {
                    BudgetAccountBE newAccount = controller.createSubBudget(mAccount, subBudgetName, currentMonthBudget, yearlyBudget);
                    if (newAccount == null)
                        showToastLong(R.string.toast_error_account_name_taken);
                    else {
                        int count = budgetViews.size();
                        if (count > 0) {
                            // get previously last subBudget and set it to isLast = false
                            BudgetAccountTableRow last = budgetViews.get(count - 1);
                            last.setIsLast(false);
                            last.updateUI();
                        }
                        // create new BudgetAccountTableRow object and add it to the rootLayout
                        BudgetAccountTableRow newRow = BudgetAccountTableRow.getInstance(self);
                        // refresh other rows before this one gets added
                        onRefresh();
                        newRow.init(self, newAccount, true);
                        TableLayout rootLayout = findViewById(R.id.sub_budget_overview_table_layout);
                        newRow.populateUI();
                        rootLayout.addView(newRow);
                        showToastLong(R.string.toast_success_sub_budget_created);
                    }
                } catch (JSONException | IOException e) {
                    showErrorToast(e);
                }
            }
        };
        dialog.show();
    }

    public void showCreateProjectBudgetDialog() {
        BudgetAccountDetailsActivity self = this;
        CreateBudgetAccountDialog dialog = new CreateBudgetAccountDialog(this, true) {
            @Override
            public void onConfirm(String subBudgetName, float currentMonthBudget, float yearlyBudget) {
                try {
                    ProjectBudgetBE newAccount = controller.createProjectBudget(mAccount, subBudgetName, yearlyBudget);
                    if (newAccount == null)
                        showToastLong(R.string.toast_error_account_name_taken);
                    else {
                        int count = budgetViews.size();
                        if (count > 0) {
                            // get previously last subBudget and set it to isLast = false
                            BudgetAccountTableRow last = budgetViews.get(count - 1);
                            last.setIsLast(false);
                            last.updateUI();
                        }
                        // create new BudgetAccountTableRow object and add it to the rootLayout
                        BudgetAccountTableRow newRow = BudgetAccountTableRow.getInstance(self);
                        newRow.init(self, newAccount, true);
                        TableLayout rootLayout = findViewById(R.id.sub_budget_overview_table_layout);
                        newRow.populateUI();
                        rootLayout.addView(newRow);
                        onRefresh();
                        showToastLong(R.string.toast_success_sub_budget_created);
                    }
                } catch (JSONException | IOException e) {
                    showErrorToast(e);
                }
            }
        };
        dialog.show();
    }

    public void showTransferSubBudgetDialog() {
        TransferSubBudgetDialog transferDialog = new TransferSubBudgetDialog(this,
                mAccount, model.getAllBudgetAccounts()) {
            @Override
            public void onConfirm(BudgetAccountBE subBudget, BudgetAccountBE target) {
                try {
                    boolean result = controller.transferSubBudget(mAccount, subBudget, target);
                    if (result)
                        showToastLong(R.string.toast_success_sub_budget_transferred);
                    else {
                        showToastLong(R.string.toast_error_invalid_request);
                        return;
                    }
                    onRefresh();
                } catch (JSONException | IOException e) {
                    showErrorToast(e);
                }
            }
        };
        transferDialog.show();
    }

    public void showEditRenewalDialog() {
        EditRenewalDialog dialog = new EditRenewalDialog(this, mAccount) {
            @Override
            public void onConfirm(int renewalPeriod, String nextRenewal) {
                try {
                    mAccount.setRenewalPeriod(renewalPeriod);
                    mAccount.setNextRenewal(nextRenewal);
                    controller.saveAccountsToInternal();
                    String renewalString = String.format(Locale.US, "%01dM (%s)", renewalPeriod, nextRenewal);
                    tvRenewal.setText(renewalString);
                } catch (IllegalArgumentException | JSONException | IOException e) {
                    showErrorToast(e);
                }
            }
        };
        dialog.show();
    }
    // endregion

    @SuppressLint("DefaultLocale")
    private void updateUITotalSums() {
        float totalSum = mAccount.getTotalSum();
        float current_budget = mAccount.getTotalAvailableBudget();
        float allotted_budget = mAccount.getMeanAllottedTotalBudget();

        float current_percentage = BudgetFigures.render(totalValue, totalPercentage, totalYearly,
                totalSum, current_budget, allotted_budget, mAccount.getTotalYearlyBudget());

        totalPercentage.setBackground(PercentageBackground.evaluatePercentageBG(current_percentage, this));
    }

    private void loadSubBudgets() {
        budgetViews = new ArrayList<>();
        List<BudgetAccountBE> firstLevelBudgets = mAccount.getDirectSubBudgets();
        int count = firstLevelBudgets.size();
        for (int index = 0; index < count; index++) {
            BudgetAccountBE currentAccount = firstLevelBudgets.get(index);
            BudgetAccountTableRow newRow = BudgetAccountTableRow.getInstance(this);
            newRow.init(this, currentAccount, index == count - 1);
        }
    }

    private void reloadUI() {
        budgetViews = new ArrayList<>();
        loadSubBudgets();
        // This will ensure your UI is up-to-date with the current state of budgetViews
        runOnUiThread(this::populateUI);
    }
}
