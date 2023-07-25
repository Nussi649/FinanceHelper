package Logic;

import java.util.ArrayList;
import java.util.List;

public class BudgetAccountBE extends AccountBE{

    public float indivYearlyBudget;
    public float indivCurrentBudget = -1.0f;

    private List<BudgetAccountBE> subBudgets;
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
        this.indivCurrentBudget = indivYearlyBudget / 12;
    }

    public BudgetAccountBE(String name, float indivCurrentBudget, float indivYearlyBudget) {
        super(name);
        subBudgets = new ArrayList<>();
        this.indivCurrentBudget = indivCurrentBudget;
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

    public void setIndivCurrentBudget(float currentBudget) {
        this.indivCurrentBudget = currentBudget;
    }

    public void setToOtherEntity(String otherEntity) {
        toOtherEntity = otherEntity;
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
    public float getTotalSum() {
        return getSum() + getSubSum();
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

    public float getTotalCurrentBudget() {
        return indivCurrentBudget + getSubCurrentBudget();
    }

    public float getSubCurrentBudget() {
        float sum = 0;
        for (BudgetAccountBE subBudget : subBudgets)
            sum += subBudget.getTotalCurrentBudget();
        return sum;
    }

    public String getOtherEntity(){
        return toOtherEntity;
    }
    // endregion

    @Override
    public void reset() {
        indivCurrentBudget += indivYearlyBudget/12 - getSum();
        txList = new ArrayList<>();
        for (BudgetAccountBE subBudget : subBudgets) {
            subBudget.reset();
        }
    }
}
