package Logic;

import android.util.Log;
import android.widget.TableLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import Backend.Util;
import View.BudgetAccountTableRow;

public class BudgetAccountBE extends AccountBE{

    public float indivYearlyBudget;
    public float indivAvailableBudget = -1.0f;

    protected int renewalPeriod = 1;
    protected String nextRenewal;

    private List<BudgetAccountBE> subBudgets;
    // toOtherEntity specifies if payments received on this account should be accounted for as income on another financial entity
    // can be left empty ("")
    protected String toOtherEntity;

    // region constructors
    public BudgetAccountBE(String name) {
        super(name);
        subBudgets = new ArrayList<>();
    }

    public BudgetAccountBE(AccountBE source) {
        super(source.getName());
        txList = source.txList;
        isActive = source.isActive;
        subBudgets = new ArrayList<>();
    }

    public BudgetAccountBE(String name, float indivYearlyBudget) {
        super(name);
        subBudgets = new ArrayList<>();
        this.indivYearlyBudget = indivYearlyBudget;
        this.indivAvailableBudget = indivYearlyBudget / 12;
    }

    public BudgetAccountBE(String name, float indivAvailableBudget, float indivYearlyBudget) {
        super(name);
        subBudgets = new ArrayList<>();
        this.indivAvailableBudget = indivAvailableBudget;
        this.indivYearlyBudget = indivYearlyBudget;
    }
    // endregion

    // region set functions
    public void setSubBudgets(List<BudgetAccountBE> sub_budgets_in) {
        this.subBudgets = sub_budgets_in;
    }

    public void addSubBudget(BudgetAccountBE newSubBudget) {
        this.subBudgets.add(newSubBudget);
    }

    public void setIndivYearlyBudget(float yearlyBudget) {
        this.indivYearlyBudget = yearlyBudget;
    }

    public void adjustIndivYearlyBudget(float delta) {
        this.indivYearlyBudget += delta;
    }
    public void setIndivAvailableBudget(float availableBudget) {
        this.indivAvailableBudget = availableBudget;
    }

    public void setToOtherEntity(String otherEntity) {
        toOtherEntity = otherEntity;
    }

    public void setRenewalPeriod(int duration) {
        renewalPeriod = duration;
    }

    public void setNextRenewal(String renewalPeriod) throws IllegalArgumentException {
        // validate input
        if (!Util.validatePeriod(renewalPeriod))
            throw new IllegalArgumentException("The period should have the format 'YYYY-MM' and be between 2000 and 2050.");
        nextRenewal = renewalPeriod;
    }

    public void incrementRenewalPeriod() {
        // Parse the year and month from the current nextRenewal
        String[] parts = nextRenewal.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);

        // Increment the month by the renewalPeriod
        month += renewalPeriod;

        // If the new month exceeds 12, increment the year and adjust the month
        while (month > 12) {
            year += 1;
            month -= 12;
        }

        // Update nextRenewal
        nextRenewal = String.format(Locale.US, "%04d-%02d", year, month);
    }
    // endregion

    // region get functions
    public float getSubSum() {
        // returns sum of all expenses attributed to the complete sub budget tree
        float sum = 0.0f;
        for (BudgetAccountBE subBudget : subBudgets)
            sum += subBudget.getTotalSum();
        return sum;
    }

    public float getSubSum(String period) {
        // returns sum of all expenses within the specified period attributed to the complete sub budget tree
        float sum = 0.0f;
        for (BudgetAccountBE subBudget : subBudgets)
            sum += subBudget.getTotalSum(period);
        return sum;
    }

    public float getTotalSum() {
        return getSum() + getSubSum();
    }

    public float getTotalSum(String period) {
        return getSum(period) + getSubSum(period);
    }

    public List<BudgetAccountBE> getDirectSubBudgets() {
        // returns list of direct child sub budgets
        return this.subBudgets;
    }

    public List<BudgetAccountBE> getAllSubBudgets() {
        // returns list of all subordinate sub budgets (children including their children)
        List<BudgetAccountBE> subs = new ArrayList<>();
        for (BudgetAccountBE acc : subBudgets) {
            subs.add(acc);
            subs.addAll(acc.getAllSubBudgets());
        }
        return subs;
    }

    public float getTotalYearlyBudget() {
        return indivYearlyBudget + getSubYearlyBudget();
    }

    public float getSubYearlyBudget() {
        float sum = 0;
        for (BudgetAccountBE subBudget : subBudgets)
            sum += subBudget.getTotalYearlyBudget();
        return sum;
    }

    public float getTotalAvailableBudget() {
        return indivAvailableBudget + getSubAvailableBudget();
    }

    public float getSubAvailableBudget() {
        float sum = 0;
        for (BudgetAccountBE subBudget : subBudgets)
            sum += subBudget.getTotalAvailableBudget();
        return sum;
    }

    public BudgetAccountBE getSubBudgetParent(BudgetAccountBE subBudget) {
        if (subBudgets.contains(subBudget))
            return this;
        else
            for (BudgetAccountBE iterator : subBudgets) {
                BudgetAccountBE result = iterator.getSubBudgetParent(subBudget);
                if (result != null)
                    return result;
            }
        return null;
    }

    public String getOtherEntity(){
        return toOtherEntity;
    }

    public int getRenewalPeriod() {
        return renewalPeriod;
    }

    public String getNextRenewal() {
        return nextRenewal;
    }

    public float getMeanAllottedIndivBudget() {
        return indivYearlyBudget * renewalPeriod / 12;
    }

    public float getMeanAllottedSubBudget() {
        float result = 0;
        for (BudgetAccountBE subBudget : subBudgets) {
            result += subBudget.getMeanAllottedTotalBudget();
        }
        return result;
    }

    public float getMeanAllottedTotalBudget() {
        return getMeanAllottedIndivBudget() + getMeanAllottedSubBudget();
    }
    // endregion

    @Override
    public void tryRenew() {
        try {
            if (!Util.isAfter(nextRenewal, Util.getCurrentPeriod())) {
                indivAvailableBudget += getMeanAllottedIndivBudget() - getSum();
                txList = new ArrayList<>();
                incrementRenewalPeriod();
            }
        } catch (IllegalArgumentException e) {
            Log.println(Log.ERROR, "budget_renew",
                    String.format("This Error should not occur. If it does, the code is buggy. %s", e));
        }
        for (BudgetAccountBE subBudget : subBudgets) {
            subBudget.tryRenew();
        }
    }

    @Override
    public void reset() {
        super.reset();
        indivAvailableBudget = getMeanAllottedIndivBudget();
        for (BudgetAccountBE subBudget : subBudgets) {
            subBudget.reset();
        }
    }

    public boolean transferSubBudget(BudgetAccountBE subBudget, BudgetAccountBE target) {
        if (!subBudgets.contains(subBudget))
            return false;
        target.addSubBudget(subBudget);
        boolean result = subBudgets.remove(subBudget);
        if (result)
            return true;
        target.subBudgets.remove(subBudget);
        return false;
    }

    public boolean addBudgetAccountViewsToContainer(List<BudgetAccountTableRow> viewPool, TableLayout container) {
        // initiate variables
        boolean result = true;
        BudgetAccountTableRow own = null;
        List<BudgetAccountTableRow> reducedPool = new ArrayList<>(viewPool);
        // find corresponding row
        for (BudgetAccountTableRow row : viewPool)
            if (equals(row.getReferenceAccount()))
                own = row;
        // add row to container if found otherwise set result false
        if (own != null) {
            container.addView(own);
            reducedPool.remove(own);
        }
        else
            result = false;
        // recursively add sub budgets
        for (BudgetAccountBE subBudget : subBudgets)
            result = result && subBudget.addBudgetAccountViewsToContainer(reducedPool, container);
        return result;
    }
}
