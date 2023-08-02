package com.privat.pitz.financehelper;

import android.annotation.SuppressLint;
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

import Backend.BudgetAccountListHandler;
import Backend.Util;
import Logic.AccountBE;
import Logic.BudgetAccountBE;
import Logic.ProjectBudgetBE;
import Logic.TxBE;
import View.BudgetAccountTableRow;
import View.Dialogs.CreateBudgetAccountDialog;
import View.Dialogs.EditRenewalDialog;
import View.Dialogs.SetYearlyBudgetDialog;
import View.Dialogs.TransferAvailableBudgetDialog;
import View.Dialogs.TransferSubBudgetDialog;

public class BudgetAccountDetailsActivity extends AssetAccountDetailsActivity implements BudgetAccountListHandler {
    BudgetAccountBE mAccount;
    List<BudgetAccountTableRow> budgetViews = new ArrayList<>();
    LinearLayout rootLayout;
    TextView totalValue;
    TextView totalPercentage;
    TextView totalYearly;
    TextView tvRenewal;

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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_budgetaccount_details, menu);
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
        }
        return super.onOptionsItemSelected(item);
    }
    // endregion

    // region AssetAccountDetailsActivity Overrides
    @Override
    protected void setContentLayout() {
        setContentView(R.layout.activity_budget_account_details);
    }

    @Override
    protected void initViews() {
        super.initViews();
        rootLayout = findViewById(R.id.root_layout);
        tvRenewal = findViewById(R.id.tv_renewal);
        indivYearly.setVisibility(View.VISIBLE);
        indivPercentage.setVisibility(View.VISIBLE);
        View containerTotal = findViewById(R.id.container_total_sum);
        totalValue = containerTotal.findViewById(R.id.total_current_value);
        totalPercentage = containerTotal.findViewById(R.id.total_current_percentage);
        totalYearly = containerTotal.findViewById(R.id.total_yearly_budget);
    }

    @Override
    protected void redirectAfterAccountDelete() {
        startActivity(BudgetsActivity.class);
    }

    @SuppressLint("DefaultLocale")
    @Override
    protected void setTxSum(float newValue) {
        float allotted_budget = mAccount.getMeanAllottedIndivBudget();

        float current_percentage = Util.calculateAdvancedPercentage(mAccount.indivAvailableBudget, newValue, allotted_budget);
        String currentBudgetString = Util.formatToFixedLength(Util.formatLargeFloatDisplay(mAccount.indivAvailableBudget),5);
        String currentSumString = String.format("%sx / %sx",
                Util.formatLargeFloatDisplay(newValue),
                currentBudgetString);
        String currentPercentageString = String.format("%.0f%%",
                current_percentage * 100);
        String yearly_budget_string = Util.formatLargeFloatShort(mAccount.indivYearlyBudget) + "x";
        // get currency character
        String currency = getString(R.string.label_currency);
        // set values of total sum text views
        indivValue.setText(currentSumString.replace("x", currency));
        indivPercentage.setText(currentPercentageString);
        indivYearly.setText(yearly_budget_string.replace("x", currency));
    }

    @Override
    protected void setTitle() {
        // needs to override because mAccount is different from parent's mAccount
        setCustomTitle();
        setCustomTitleDetails(mAccount.toString());
    }

    @Override
    protected boolean hasEntries() {
        // needs to override because mAccount is different from parent's mAccount
        return mAccount.getTxList().size() > 0;
    }

    @Override
    protected List<TxBE> getEntries() {
        // needs to override because mAccount is different from parent's mAccount
        return mAccount.getTxList();
    }

    @Override
    protected AccountBE getReference() {
        return mAccount;
    }

    @SuppressLint("DefaultLocale")
    protected void populateUI() {
        // populate tx entries
        super.populateUI();
        // if budget is a project budget, color background
        if (mAccount instanceof ProjectBudgetBE) {
            rootLayout.setBackgroundColor(getColor(R.color.colorSecondaryLight));
            findViewById(R.id.renewal_container).setVisibility(View.GONE);
        } else {
            String renewalString = String.format(Locale.US, "%01dM (%s)", mAccount.getRenewalPeriod(), mAccount.getNextRenewal());
            tvRenewal.setText(renewalString);
            tvRenewal.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditRenewalDialog();
                }
            });
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
        setTxSum(mAccount.getSum());
        updateUITotalSums();
    }
    // endregion

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

        float current_percentage = Util.calculateAdvancedPercentage(current_budget, totalSum, allotted_budget);

        // set values of total sum text views
        String currentBudgetString = Util.formatToFixedLength(Util.formatLargeFloatShort(current_budget),5);
        String currentSumString = String.format("%s / %s",
                Util.formatLargeFloatShort(totalSum),
                currentBudgetString);
        String currentPercentageString = String.format("%.0f%%",
                current_percentage * 100);
        String yearly_budget_string = Util.formatLargeFloatShort(mAccount.getTotalYearlyBudget());
        totalValue.setText(currentSumString);
        totalPercentage.setText(currentPercentageString);
        totalYearly.setText(yearly_budget_string);

        totalPercentage.setBackground(Util.evaluatePercentageBG(current_percentage, this));
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                populateUI();
            }
        });
    }
}